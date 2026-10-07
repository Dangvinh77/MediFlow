package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.*;
import com.mediflow.report.application.service.OperationalContributionApplicationService;
import com.mediflow.report.application.mapper.LabOperationalContributionMapper;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import com.mediflow.report.domain.model.OperationalContribution;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import com.mediflow.report.infrastructure.persistence.adapter.OperationalContributionPersistenceAdapter;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OperationalContributionPersistenceAdapter.class, OperationalContributionApplicationService.class,
        OperationalContributionPostgresTest.JsonConfig.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OperationalContributionPostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired OperationalContributionApplicationService service;
    @Autowired JdbcTemplate jdbc;

    private final UUID sourceId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final Instant time = Instant.parse("2026-10-01T08:00:00.123456789Z");

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE operational_source_snapshot, operational_delivery, operational_event_journal, operational_contribution, daily_operational_report CASCADE");
    }

    @Test
    void sameEventAndSameSourceNewEventId_onlyCountOnceAndKeepBothJournalDeliveries() {
        var first = command(UUID.randomUUID(), sourceId, departmentId, time);
        service.apply(first);
        service.apply(first);
        service.apply(command(UUID.randomUUID(), sourceId, departmentId, time));
        assertThat(count("operational_event_journal")).isEqualTo(2);
        assertThat(count("operational_delivery")).isEqualTo(2);
        assertThat(count("operational_contribution")).isOne();
        assertThat(total(null)).isOne();
        assertThat(total(departmentId)).isOne();
    }

    @Test
    void huyActualProducerBytes_replayCountsOnceAndRetainsNoNarrativeOrPatientData() throws Exception {
        var decoder=new CareFinanceEnvelopeDecoder(new ObjectMapper());
        var mapper=new com.mediflow.report.application.mapper.SurgeryOperationalContributionMapper(ZoneId.of("Asia/Bangkok"));
        var path=Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.completed.admission.v1.json");
        var event=decoder.decode("surgery.completed",Files.readAllBytes(path));
        service.apply(mapper.map(event));service.apply(mapper.map(event));
        var metadata=event.metadata();
        var redelivery=new CareFinanceEventMetadata(UUID.randomUUID(),metadata.eventType(),metadata.version(),
                metadata.occurredAt(),metadata.correlationId(),metadata.producer(),metadata.sourceField(),metadata.sourceId());
        service.apply(mapper.map(new DecodedCareFinanceEvent(redelivery,event.payload())));
        assertThat(count("operational_contribution")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT surgeries_completed,surgery_duration_minutes FROM daily_operational_report"))
                .hasSize(2).allSatisfy(row->{assertThat(row.get("surgeries_completed")).isEqualTo(1L);
                    assertThat(row.get("surgery_duration_minutes")).isEqualTo(6L);});
        assertThat(jdbc.queryForList("SELECT event_snapshot::text || contribution_snapshot::text FROM operational_event_journal",String.class))
                .allSatisfy(snapshot->assertThat(snapshot).doesNotContain("patientId","performedItems","complicationsSummary","Indication"));
    }

    @Test void surgery_sameResultNewDeliveryChangedNonMetricEvidence_isConflictAndRollsBack() throws Exception {
        var decoder = new CareFinanceEnvelopeDecoder(new ObjectMapper());
        var mapper = new com.mediflow.report.application.mapper.SurgeryOperationalContributionMapper(ZoneId.of("Asia/Bangkok"));
        var event = decoder.decode("surgery.completed", Files.readAllBytes(Path.of(
                "../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.completed.admission.v1.json")));
        service.apply(mapper.map(event));
        var changed = new LinkedHashMap<>(event.payload()); changed.put("performedItems", List.of());
        var meta = event.metadata();
        var changedEvent = new DecodedCareFinanceEvent(new CareFinanceEventMetadata(UUID.randomUUID(),
                meta.eventType(),1,meta.occurredAt(),meta.correlationId(),meta.producer(),meta.sourceField(),meta.sourceId()), changed);
        assertThatThrownBy(() -> service.apply(mapper.map(changedEvent))).hasMessageContaining("conflict");
        assertThat(count("operational_event_journal")).isOne();
        assertThat(count("operational_source_snapshot")).isOne();
        assertThat(count("operational_contribution")).isEqualTo(2);
    }

    @Test void upgradedSourceWithoutRawPayloadHash_cannotBeSilentlyRebasedByNewDelivery() {
        var command = command(UUID.randomUUID(), sourceId, departmentId, time);
        jdbc.update("""
                INSERT INTO operational_source_snapshot(source_type,source_id,source_revision,event_type,
                    first_event_id,evidence_state) VALUES ('MEDICAL_RECORD',?,1,'medicalrecord.completed',?,'LEGACY_UNVERIFIED')
                """, sourceId, UUID.randomUUID());
        assertThatThrownBy(() -> service.apply(command)).hasMessageContaining("revalidation");
        assertThat(count("operational_event_journal")).isZero();
        assertThat(count("operational_contribution")).isZero();
    }

    @Test
    void sameEventDifferentEnvelopeAndSameSourceDifferentDepartmentOrNanos_areConflicts() {
        var first = command(UUID.randomUUID(), sourceId, departmentId, time);
        service.apply(first);
        var changedPayload = new LinkedHashMap<>(first.event().payload());
        changedPayload.put("disposition", "changed");
        var changedEvent = new DecodedCareFinanceEvent(first.event().metadata(), changedPayload);
        assertThatThrownBy(() -> service.apply(new ApplyOperationalContributionCommand(changedEvent, first.contributions())))
                .hasMessageContaining("conflict");
        assertThatThrownBy(() -> service.apply(command(UUID.randomUUID(), sourceId, UUID.randomUUID(), time)))
                .hasMessageContaining("conflict");
        assertThatThrownBy(() -> service.apply(command(UUID.randomUUID(), sourceId, departmentId, time.plusNanos(1))))
                .hasMessageContaining("conflict");
        assertThat(count("operational_event_journal")).isOne();
        assertThat(total(null)).isOne();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"clinical", "pharmacy"})
    void actualClinicalAndPharmacyProducerBytes_updateBothScopesOnceWithoutPatientJournal(String source) throws Exception {
        var decoder = new CareFinanceEnvelopeDecoder(new ObjectMapper());
        String eventType = source.equals("clinical") ? "medicalrecord.completed" : "prescription.filled";
        Path path = source.equals("clinical")
                ? Path.of("../clinical-service/src/test/resources/contracts/medicalrecord.completed.v1.json")
                : Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.v1.json");
        var event = decoder.decode(eventType, Files.readAllBytes(path));
        java.util.function.Function<DecodedCareFinanceEvent, ApplyOperationalContributionCommand> mapper = source.equals("clinical")
                ? new com.mediflow.report.application.mapper.ClinicalOperationalContributionMapper(ZoneId.of("Asia/Bangkok"))::map
                : new com.mediflow.report.application.mapper.PrescriptionOperationalContributionMapper(ZoneId.of("Asia/Bangkok"))::map;
        service.apply(mapper.apply(event)); service.apply(mapper.apply(event));
        var metadata = event.metadata();
        service.apply(mapper.apply(new DecodedCareFinanceEvent(new CareFinanceEventMetadata(UUID.randomUUID(),
                eventType, 1, metadata.occurredAt(), metadata.correlationId(), metadata.producer(),
                metadata.sourceField(), metadata.sourceId()), event.payload())));
        assertThat(count("operational_contribution")).isEqualTo(source.equals("clinical") ? 1 : 2);
        assertThat(jdbc.queryForList("SELECT completed_visits,dispensed_prescriptions,dispensed_units FROM daily_operational_report"))
                .hasSize(2).allSatisfy(row -> {
                    assertThat(row.get("completed_visits")).isEqualTo(source.equals("clinical") ? 1L : 0L);
                    assertThat(row.get("dispensed_prescriptions")).isEqualTo(source.equals("pharmacy") ? 1L : 0L);
                    assertThat(row.get("dispensed_units")).isEqualTo(source.equals("pharmacy") ? 2L : 0L);
                });
        assertThat(jdbc.queryForList("SELECT event_snapshot::text || contribution_snapshot::text FROM operational_event_journal", String.class))
                .allSatisfy(snapshot -> assertThat(snapshot).doesNotContain("patientId", "drugName", "dosage", "diagnosis"));
    }

    @Test
    void secondScopeFails_rollsBackJournalDeliveryContributionAndHospitalScope() {
        jdbc.execute("""
                CREATE FUNCTION reject_department_scope() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected second-scope failure'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_department BEFORE INSERT ON daily_operational_report
                FOR EACH ROW WHEN (NEW.department_id IS NOT NULL) EXECUTE FUNCTION reject_department_scope()
                """);
        var command = command(UUID.randomUUID(), sourceId, departmentId, time);
        try {
            assertThatThrownBy(() -> service.apply(command)).hasMessageContaining("injected second-scope failure");
            assertThat(count("operational_event_journal")).isZero();
            assertThat(count("operational_delivery")).isZero();
            assertThat(count("operational_contribution")).isZero();
            assertThat(count("daily_operational_report")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_department ON daily_operational_report");
            jdbc.execute("DROP FUNCTION reject_department_scope()");
        }
        service.apply(command);
        assertThat(total(null)).isOne();
    }

    @Test
    void concurrentSameBusinessSourceDifferentEventIds_hasOneEffect() throws Exception {
        race(command(UUID.randomUUID(), sourceId, departmentId, time), command(UUID.randomUUID(), sourceId, departmentId, time));
        assertThat(count("operational_event_journal")).isEqualTo(2);
        assertThat(count("operational_contribution")).isOne();
        assertThat(total(null)).isOne();
    }

    @Test
    void concurrentDifferentBusinessSources_newScopeUpsertLosesNoCount() throws Exception {
        race(command(UUID.randomUUID(), sourceId, departmentId, time), command(UUID.randomUUID(), UUID.randomUUID(), departmentId, time));
        assertThat(count("operational_contribution")).isEqualTo(2);
        assertThat(total(null)).isEqualTo(2);
        assertThat(total(departmentId)).isEqualTo(2);
    }

    @Test
    void multiMetricPrescription_hasOneJournalAndAtomicDepartmentHospitalTotals() {
        UUID eventId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        var event = new DecodedCareFinanceEvent(new CareFinanceEventMetadata(eventId, "prescription.filled",
                1, time, "trace", "pharmacy-service", "dispenseId", sourceId), Map.of("dispenseId", sourceId.toString()));
        var prescriptions = new OperationalContribution(eventId, "DISPENSE", sourceId, 1, Metric.DISPENSED_PRESCRIPTIONS,
                departmentId, "ADMISSION", episodeId, LocalDate.of(2026, 10, 1), BigDecimal.ONE, null, time);
        var units = new OperationalContribution(eventId, "DISPENSE", sourceId, 1, Metric.DISPENSED_UNITS,
                departmentId, "ADMISSION", episodeId, LocalDate.of(2026, 10, 1), BigDecimal.TEN, null, time);
        var command = new ApplyOperationalContributionCommand(event, List.of(units, prescriptions));
        service.apply(command);
        service.apply(command);
        assertThat(count("operational_event_journal")).isOne();
        assertThat(count("operational_contribution")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT dispensed_prescriptions, dispensed_units FROM daily_operational_report"))
                .hasSize(2).allSatisfy(row -> {
                    assertThat(row.get("dispensed_prescriptions")).isEqualTo(1L);
                    assertThat(row.get("dispensed_units")).isEqualTo(10L);
                });
    }

    @Test
    void journalKeepsReplayInputsButDoesNotStoreClinicalResultsOrFreeText() {
        var command = command(UUID.randomUUID(), sourceId, departmentId, time);
        var payload = new LinkedHashMap<>(command.event().payload());
        payload.put("diagnosis", "PRIVATE CLINICAL TEXT");
        payload.put("results", List.of(Map.of("indicator", "PRIVATE RESULT")));
        var event = new DecodedCareFinanceEvent(command.event().metadata(), payload);
        service.apply(new ApplyOperationalContributionCommand(event, command.contributions()));
        String stored = jdbc.queryForObject("""
                SELECT event_snapshot::text || contribution_snapshot::text FROM operational_event_journal
                """, String.class);
        assertThat(stored).contains(sourceId.toString(), departmentId.toString(), "COMPLETED_VISITS")
                .doesNotContain("PRIVATE", "diagnosis", "results");
    }

    @Test
    void actualLabProducerBytes_mapToBothScopesAndRedeliveryDoesNotCountTwice() throws Exception {
        var decoder = new CareFinanceEnvelopeDecoder(new ObjectMapper());
        var mapper = new LabOperationalContributionMapper(ZoneId.of("Asia/Bangkok"));
        var event = decoder.decode("lab.result.created", labFixture("lab.result.created.v1.json"));
        var command = mapper.map(event);
        service.apply(command);
        service.apply(command);
        var metadata = event.metadata();
        var republished = new DecodedCareFinanceEvent(new CareFinanceEventMetadata(UUID.randomUUID(),
                metadata.eventType(), 1, metadata.occurredAt().plusSeconds(86400), "republish-test",
                metadata.producer(), metadata.sourceField(), metadata.sourceId()), event.payload());
        service.apply(mapper.map(republished));
        assertThat(count("operational_contribution")).isOne();
        assertThat(count("operational_event_journal")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT report_date::text, lab_tests FROM daily_operational_report"))
                .hasSize(2).allSatisfy(row -> {
                    assertThat(row.get("report_date")).isEqualTo("2026-09-28");
                    assertThat(row.get("lab_tests")).isEqualTo(1L);
                });
        String journal = jdbc.queryForObject("""
                SELECT event_snapshot::text || contribution_snapshot::text FROM operational_event_journal WHERE event_id = ?
                """, String.class, metadata.eventId());
        assertThat(journal).doesNotContain("Hemoglobin", "results", "conclusion", "patientId", "verifiedBy");
    }

    @Test
    void syntheticLabVersionThree_doNotClaimOrMutateUntilCorrectionContractExists() throws Exception {
        var decoder = new CareFinanceEnvelopeDecoder(new ObjectMapper());
        var mapper = new LabOperationalContributionMapper(ZoneId.of("Asia/Bangkok"));
        var event = decoder.decode("lab.result.created", labFixture("lab.result.created.admission.v1.json"));
        var payload = new LinkedHashMap<>(event.payload());
        payload.put("resultVersion", 3);
        var unsupportedCorrection = new DecodedCareFinanceEvent(event.metadata(), payload);
        assertThatThrownBy(() -> service.apply(mapper.map(unsupportedCorrection)))
                .hasMessageContaining("corrections");
        assertThat(count("operational_event_journal")).isZero();
        assertThat(count("operational_delivery")).isZero();
        assertThat(count("operational_contribution")).isZero();
        assertThat(count("daily_operational_report")).isZero();
    }

    private static byte[] labFixture(String file) throws Exception {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("backend/report-service/pom.xml"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Repository root not found");
        return Files.readAllBytes(current.resolve("backend/lab-service/src/test/resources/contracts/" + file));
    }

    private ApplyOperationalContributionCommand command(UUID eventId, UUID source, UUID department, Instant occurredAt) {
        var event = new DecodedCareFinanceEvent(new CareFinanceEventMetadata(eventId, "medicalrecord.completed", 1,
                occurredAt, "trace", "clinical-service", "recordId", source), Map.of("recordId", source.toString()));
        var fact = new OperationalContribution(eventId, "MEDICAL_RECORD", source, 1, Metric.COMPLETED_VISITS,
                department, null, null, LocalDate.of(2026, 10, 1), BigDecimal.ONE, "OUTPATIENT", occurredAt);
        return new ApplyOperationalContributionCommand(event, fact);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private long total(UUID departmentId) {
        return jdbc.queryForObject("SELECT completed_visits FROM daily_operational_report WHERE department_id IS NOT DISTINCT FROM ?",
                Long.class, departmentId);
    }

    private void race(ApplyOperationalContributionCommand first, ApplyOperationalContributionCommand second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var one = executor.submit(() -> { await(ready, go); service.apply(first); });
            var two = executor.submit(() -> { await(ready, go); service.apply(second); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration
    static class JsonConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
