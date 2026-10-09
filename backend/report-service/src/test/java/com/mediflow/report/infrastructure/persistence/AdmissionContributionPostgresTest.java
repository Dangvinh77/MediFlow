package com.mediflow.report.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.dto.response.OperationalReplayProgress;
import com.mediflow.report.application.mapper.AdmissionReportFactMapper;
import com.mediflow.report.application.service.AdmissionReportEvidenceApplicationService;
import com.mediflow.report.application.service.OperationalContributionApplicationService;
import com.mediflow.report.application.service.OperationalReplayApplicationService;
import com.mediflow.report.application.service.OperationalReportFactReceiver;
import com.mediflow.report.infrastructure.persistence.adapter.AdmissionReportEvidencePersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.OperationalContributionPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.OperationalReplayPersistenceAdapter;
import com.mediflow.report.support.AdmissionEvidenceTestFixtures;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** Actual Inpatient bytes, real transaction boundaries/replicas; not historical coverage approval. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdmissionReportEvidencePersistenceAdapter.class, AdmissionReportEvidenceApplicationService.class,
        OperationalContributionPersistenceAdapter.class, OperationalContributionApplicationService.class,
        OperationalReplayPersistenceAdapter.class, OperationalReplayApplicationService.class,
        AdmissionContributionPostgresTest.Config.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdmissionContributionPostgresTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired OperationalReportFactReceiver receiver;
    @Autowired OperationalReplayApplicationService replay;
    @Autowired AdmissionReportEvidenceApplicationService evidence;
    @Autowired JdbcTemplate jdbc;
    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean AdmissionReportFactMapper admissionMapper() { return new AdmissionReportFactMapper(); }
        @Bean OperationalReportFactReceiver receiver(OperationalContributionApplicationService operations,
                AdmissionReportEvidenceApplicationService admissions) {
            return new OperationalReportFactReceiver(operations, admissions, ZoneId.of("Asia/Bangkok"));
        }
    }
    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE operational_replay_generation, operational_source_snapshot, operational_delivery, "
                + "operational_event_journal, operational_contribution, daily_operational_report, report_admission_delivery, report_admission_target CASCADE");
    }
    @Test void start_originalBytesAndNewDeliveryCountOnceInBothScopes() throws Exception {
        var event = AdmissionEvidenceTestFixtures.fixture("admission.started");
        receiver.receive(event); receiver.receive(event); receiver.receive(redelivery(event));
        assertThat(count("report_admission_delivery")).isEqualTo(2);
        assertThat(count("operational_event_journal")).isEqualTo(2);
        assertThat(count("operational_contribution")).isOne();
        assertThat(jdbc.queryForList("SELECT admissions FROM daily_operational_report", Long.class)).hasSize(2).containsOnly(1L);
        assertThat(jdbc.queryForList("SELECT event_snapshot::text || contribution_snapshot::text FROM operational_event_journal", String.class))
                .allSatisfy(snapshot -> assertThat(snapshot).doesNotContain("patientId", "bedId", "emergencyOverrideId", "settlementId"));
    }
    @Test void closeBeforeStartCountsAdmissionAtAdmittedTimeNeverDischargeOrOccupancy() throws Exception {
        receiver.receive(AdmissionEvidenceTestFixtures.fixture("admission.closed"));
        assertThat(count("operational_contribution")).isZero();
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        receiver.receive(start); receiver.receive(redelivery(start));
        assertThat(evidence.project(start).status()).isEqualTo(com.mediflow.report.domain.model.AdmissionReportHistory.Status.CLOSED);
        assertThat(jdbc.queryForList("SELECT report_date, admissions, discharges, inpatient_days FROM daily_operational_report"))
                .hasSize(2).allSatisfy(row -> {
                    assertThat(row.get("report_date").toString()).isEqualTo("2026-09-28");
                    assertThat(row.get("admissions")).isEqualTo(1L); assertThat(row.get("discharges")).isEqualTo(0L);
                    assertThat(row.get("inpatient_days")).isEqualTo(0L);
                });
    }
    @Test void failedSecondScopeRollsBackAdmissionEvidenceJournalAndDeliveryTogether() throws Exception {
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        jdbc.execute("CREATE FUNCTION reject_admission_scope() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.department_id IS NULL THEN RAISE EXCEPTION 'injected hospital failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER admission_scope_failure BEFORE INSERT OR UPDATE ON daily_operational_report FOR EACH ROW EXECUTE FUNCTION reject_admission_scope()");
        try {
            assertThatThrownBy(() -> receiver.receive(start)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            for (String table : new String[]{"report_admission_target", "report_admission_delivery", "report_admission_fact",
                    "operational_event_journal", "operational_contribution", "daily_operational_report", "operational_source_snapshot"})
                assertThat(count(table)).as(table).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER admission_scope_failure ON daily_operational_report");
            jdbc.execute("DROP FUNCTION reject_admission_scope()");
        }
        receiver.receive(start);
        assertThat(count("operational_contribution")).isOne();
    }
    @Test void conflictingEarlyClosePatientRejectsWithoutClaimOrMetric() throws Exception {
        receiver.receive(AdmissionEvidenceTestFixtures.fixture("admission.closed"));
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        assertThatThrownBy(() -> receiver.receive(AdmissionEvidenceTestFixtures.changed(start, UUID.randomUUID(), "patientId", UUID.randomUUID().toString())))
                .hasMessageContaining("patient");
        assertThat(count("report_admission_delivery")).isOne(); assertThat(count("operational_event_journal")).isZero();
        assertThat(count("operational_contribution")).isZero();
    }
    @Test void changedBedWithSameAdmissionNewEventRejectsEvenWhenCountWouldBeUnchanged() throws Exception {
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started"); receiver.receive(start);
        assertThatThrownBy(() -> receiver.receive(AdmissionEvidenceTestFixtures.changed(start, UUID.randomUUID(), "bedId", UUID.randomUUID().toString())))
                .hasMessageContaining("correction");
        assertThat(count("report_admission_delivery")).isOne(); assertThat(count("operational_event_journal")).isOne();
    }
    @Test void concurrentReplicaDeliveriesHaveOneSourceEffect() throws Exception {
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started"); var ready = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { await(ready); receiver.receive(start); });
            var second = pool.submit(() -> { await(ready); receiver.receive(redelivery(start)); });
            ready.countDown(); first.get(30, TimeUnit.SECONDS); second.get(30, TimeUnit.SECONDS);
        }
        assertThat(count("operational_contribution")).isOne(); assertThat(count("operational_event_journal")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT admissions FROM daily_operational_report", Long.class)).hasSize(2).containsOnly(1L);
    }
    @Test void finiteReplayRestoresExactAdmissionScopesWithoutMutatingLiveOrPublishing() throws Exception {
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        receiver.receive(start); receiver.receive(redelivery(start));
        var generation = replay.start();
        while (generation.status() == OperationalReplayProgress.Status.BUILDING) generation = replay.advance(generation.generationId(), 1);
        assertThat(generation.status()).isEqualTo(OperationalReplayProgress.Status.VERIFIED);
        assertThat(generation.sourceEvents()).isEqualTo(2); assertThat(generation.appliedEvents()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT numeric_value FROM operational_replay_scope WHERE generation_id=? AND metric_type='ADMISSIONS'",
                Long.class, generation.generationId())).hasSize(2).containsOnly(1L);
        assertThat(count("operational_report_publication")).isZero(); assertThat(count("operational_contribution")).isOne();
    }
    @Test void previousEvidenceOnlySourceNeedsActualByteRevalidationNotInferredBackfill() throws Exception {
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started"); evidence.project(start);
        assertThat(count("operational_event_journal")).isZero(); receiver.receive(start);
        assertThat(count("report_admission_delivery")).isOne(); assertThat(count("operational_contribution")).isOne();
    }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private static DecodedCareFinanceEvent redelivery(DecodedCareFinanceEvent event) {
        var m = event.metadata(); return new DecodedCareFinanceEvent(new CareFinanceEventMetadata(UUID.randomUUID(), m.eventType(),
                m.version(), m.occurredAt(), m.correlationId(), m.producer(), m.sourceField(), m.sourceId()), event.payload());
    }
    private static void await(CountDownLatch ready) {
        try { if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Worker start timed out"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
    }
}
