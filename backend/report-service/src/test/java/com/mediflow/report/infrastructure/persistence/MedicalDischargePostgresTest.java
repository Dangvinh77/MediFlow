package com.mediflow.report.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.mapper.AdmissionReportFactMapper;
import com.mediflow.report.application.service.*;
import com.mediflow.report.application.dto.response.OperationalReplayProgress;
import com.mediflow.report.infrastructure.persistence.adapter.*;
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

@DataJpaTest
@com.mediflow.report.support.ReportPersistenceTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdmissionReportEvidencePersistenceAdapter.class, AdmissionReportEvidenceApplicationService.class,
    OperationalContributionPersistenceAdapter.class, OperationalContributionApplicationService.class,
    OperationalReplayPersistenceAdapter.class, OperationalReplayApplicationService.class,
    MedicalDischargeStoreAdapter.class, MedicalDischargeApplicationService.class, MedicalDischargePostgresTest.Config.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MedicalDischargePostgresTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired OperationalReportFactReceiver receiver;
    @Autowired OperationalReplayApplicationService replay;
    @Autowired JdbcTemplate jdbc;
    @TestConfiguration(proxyBeanMethods = false) static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean ZoneId reportZoneId() { return ZoneId.of("Asia/Bangkok"); }
        @Bean AdmissionReportFactMapper admissionMapper() { return new AdmissionReportFactMapper(); }
        @Bean OperationalReportFactReceiver receiver(OperationalContributionApplicationService operations,
                AdmissionReportEvidenceApplicationService admissions, MedicalDischargeApplicationService medical) {
            return new OperationalReportFactReceiver(operations, admissions, ZoneId.of("Asia/Bangkok"), medical);
        }
    }
    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE operational_replay_generation, operational_source_snapshot, operational_delivery, operational_event_journal, operational_contribution, daily_operational_report, report_admission_delivery, report_admission_target CASCADE");
    }
    private void start() throws Exception { receiver.receive(AdmissionEvidenceTestFixtures.fixture("admission.started")); }
    private com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent medical() throws Exception {
        return AdmissionEvidenceTestFixtures.fixture("discharge.medically.approved");
    }
    @Test void medicalBeforeStart_durablePendingRecoveredWithExactSourceTimeAndNoDouble() throws Exception {
        receiver.receive(medical());
        assertThat(jdbc.queryForObject("SELECT status FROM report_medical_discharge", String.class)).isEqualTo("PENDING_START");
        assertThat(count("operational_event_journal")).isZero(); start();
        receiver.receive(medical()); receiver.receive(AdmissionEvidenceTestFixtures.changed(medical(), UUID.randomUUID(), null, null));
        assertThat(count("operational_contribution")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT status FROM report_medical_discharge", String.class)).isEqualTo("APPLIED");
        assertThat(jdbc.queryForList("SELECT medical_discharges,medical_los_minutes,completed_stay_days,inpatient_days FROM daily_operational_report WHERE report_date='2026-09-30'"))
                .hasSize(2).allSatisfy(row -> {
                    assertThat(row.get("medical_discharges")).isEqualTo(1L);
                    assertThat(row.get("medical_los_minutes")).isEqualTo(3290L); assertThat(row.get("completed_stay_days")).isEqualTo(2L);
                    assertThat(row.get("inpatient_days")).isEqualTo(0L);
                });
    }
    @Test void knownSourceChangedSummary_conflictsWithoutNewClaim() throws Exception {
        start(); receiver.receive(medical());
        assertThatThrownBy(() -> receiver.receive(AdmissionEvidenceTestFixtures.changed(medical(), UUID.randomUUID(), "summaryId", UUID.randomUUID().toString())))
                .hasMessageContaining("Immutable");
        assertThat(count("report_medical_discharge_delivery")).isOne(); assertThat(count("operational_contribution")).isEqualTo(4);
    }
    @Test void badEarlyPatient_isQuarantinedWithoutPoisoningValidStart() throws Exception {
        receiver.receive(AdmissionEvidenceTestFixtures.changed(medical(), UUID.randomUUID(), "patientId", UUID.randomUUID().toString()));
        start();
        assertThat(jdbc.queryForObject("SELECT status FROM report_medical_discharge", String.class)).isEqualTo("REJECTED");
        assertThat(count("operational_contribution")).isOne();
        assertThat(jdbc.queryForObject("SELECT sum(medical_discharges) FROM daily_operational_report", Long.class)).isZero();
    }
    @Test void administrativeCloseBeforeMedical_isOnlyEvidenceAndRevalidatesChronology() throws Exception {
        receiver.receive(AdmissionEvidenceTestFixtures.fixture("admission.closed")); receiver.receive(medical()); start();
        assertThat(count("operational_contribution")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT sum(medical_discharges) FROM daily_operational_report", Long.class)).isEqualTo(2L);
    }
    @Test void failedMedicalScope_rollsBackProofPendingClaimsAndAllMetricsThenRetries() throws Exception {
        start();
        jdbc.execute("CREATE FUNCTION fail_medical_scope() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.medical_los_minutes > 0 AND NEW.department_id IS NOT NULL THEN RAISE EXCEPTION 'injected'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_medical_scope BEFORE INSERT OR UPDATE ON daily_operational_report FOR EACH ROW EXECUTE FUNCTION fail_medical_scope()");
        try {
            assertThatThrownBy(() -> receiver.receive(medical())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(count("report_medical_discharge")).isZero(); assertThat(count("report_medical_discharge_delivery")).isZero();
            assertThat(count("operational_contribution")).isOne(); assertThat(count("operational_event_journal")).isOne();
        } finally {
            jdbc.execute("DROP TRIGGER fail_medical_scope ON daily_operational_report"); jdbc.execute("DROP FUNCTION fail_medical_scope()");
        }
        receiver.receive(medical()); assertThat(count("operational_contribution")).isEqualTo(4);
    }
    @Test void replicaRaceStartAndMedical_hasExactlyOnePairedEffect() throws Exception {
        var begin = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { await(begin); try { start(); } catch (Exception e) { throw new RuntimeException(e); } });
            var second = pool.submit(() -> { await(begin); try { receiver.receive(medical()); } catch (Exception e) { throw new RuntimeException(e); } });
            begin.countDown(); first.get(30, TimeUnit.SECONDS); second.get(30, TimeUnit.SECONDS);
        }
        assertThat(count("operational_contribution")).isEqualTo(4);
    }
    @Test void finiteReplay_includesAllThreeMedicalMetricsAndNoClinicalPayloadOrPublication() throws Exception {
        receiver.receive(medical()); start();
        var progress = replay.start();
        for (int i=0; i<5 && progress.status()==OperationalReplayProgress.Status.BUILDING; i++) progress=replay.advance(progress.generationId(),1);
        assertThat(progress.status()).isEqualTo(OperationalReplayProgress.Status.VERIFIED);
        assertThat(jdbc.queryForList("SELECT numeric_value FROM operational_replay_scope WHERE metric_type='MEDICAL_LOS_MINUTES'", Long.class)).hasSize(2).containsOnly(3290L);
        assertThat(jdbc.queryForList("SELECT event_snapshot::text||contribution_snapshot::text FROM operational_event_journal", String.class))
                .allSatisfy(value -> assertThat(value).doesNotContain("summaryId", "approvedBy", "patientId", "diagnosis"));
        assertThat(count("operational_report_publication")).isZero();
    }
    @Test void sharedInpatientDeliveryIdentity_cannotChangeEventKindInEitherOrder() throws Exception {
        var medical = medical(); start();
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        assertThatThrownBy(()->receiver.receive(AdmissionEvidenceTestFixtures.changed(medical,start.metadata().eventId(),null,null)))
                .hasMessageContaining("another inpatient operation");
        assertThat(count("report_medical_discharge_delivery")).isZero();
        receiver.receive(medical);
        assertThatThrownBy(()->receiver.receive(AdmissionEvidenceTestFixtures.changed(start,medical.metadata().eventId(),null,null)))
                .hasMessageContaining("medical-discharge operation");
        assertThat(count("report_admission_delivery")).isOne();
    }
    @Test void pendingMetadataCorruption_isRejectedAndNotTurnedIntoInventedReplayEvidence() throws Exception {
        receiver.receive(medical());
        jdbc.update("UPDATE report_medical_discharge SET correlation_id='corrupted'");
        assertThatThrownBy(this::start).hasMessageContaining("Immutable");
        assertThat(count("operational_contribution")).isZero(); assertThat(count("report_admission_fact")).isZero();
    }
    @Test void closeEarlierThanAlreadyAppliedMedicalFact_rollsBackCloseAndKeepsOriginalMetrics() throws Exception {
        start(); receiver.receive(medical());
        var close=AdmissionEvidenceTestFixtures.fixture("admission.closed");
        var bad=AdmissionEvidenceTestFixtures.changed(close,UUID.randomUUID(),"closedAt","2026-09-29T09:00:00Z");
        assertThatThrownBy(()->receiver.receive(bad)).hasMessageContaining("chronology");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_admission_fact WHERE fact_type='CLOSED'",Long.class)).isZero();
        assertThat(count("operational_contribution")).isEqualTo(4);
    }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class); }
    private static void await(CountDownLatch start) {
        try { if (!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
