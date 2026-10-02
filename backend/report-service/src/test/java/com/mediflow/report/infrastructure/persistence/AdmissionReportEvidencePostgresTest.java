package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.mapper.AdmissionReportFactMapper;
import com.mediflow.report.application.service.AdmissionReportEvidenceApplicationService;
import com.mediflow.report.domain.model.AdmissionReportHistory.Status;
import com.mediflow.report.infrastructure.persistence.adapter.AdmissionReportEvidencePersistenceAdapter;
import com.mediflow.report.support.AdmissionEvidenceTestFixtures;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdmissionReportEvidencePersistenceAdapter.class, AdmissionReportEvidenceApplicationService.class,
        AdmissionReportEvidencePostgresTest.Config.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdmissionReportEvidencePostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired AdmissionReportEvidenceApplicationService service;
    @Autowired AdmissionReportEvidencePersistenceAdapter evidence;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void clean() { jdbc.execute("TRUNCATE report_admission_delivery, report_admission_fact, report_admission_target CASCADE"); }

    @Test void actualCloseBeforeStart_survivesReloadAndPairsWithExactStartOnly() throws Exception {
        var close = AdmissionEvidenceTestFixtures.fixture("admission.closed");
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        assertThat(service.project(close).status()).isEqualTo(Status.PENDING_START);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            evidence.lockAdmission(close.metadata().sourceId());
            assertThat(evidence.findHistory(close.metadata().sourceId()).departmentId()).isNull();
        });
        var paired = service.project(start);
        assertThat(paired.status()).isEqualTo(Status.CLOSED);
        assertThat(paired.departmentId().toString()).isEqualTo(start.payload().get("departmentId"));
        assertThat(service.project(start)).isEqualTo(paired);
        assertThat(count("report_admission_delivery")).isEqualTo(2);
        assertThat(count("report_admission_fact")).isEqualTo(2);
        assertThat(count("operational_contribution")).isZero();
        assertThat(count("daily_operational_report")).isZero();
    }
    @Test void semanticRedelivery_keepsBothDeliveryIdsWithoutDuplicateFacts() throws Exception {
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        service.project(start);
        service.project(AdmissionEvidenceTestFixtures.changed(start, UUID.randomUUID(), null, null));
        assertThat(count("report_admission_delivery")).isEqualTo(2);
        assertThat(count("report_admission_fact")).isOne();
    }
    @Test void eventIdCollision_rollsBackClaimAndKeepsExistingFacts() throws Exception {
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        var before = service.project(start);
        var conflict = AdmissionEvidenceTestFixtures.changed(start, start.metadata().eventId(), "departmentId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> service.project(conflict)).hasMessageContaining("envelope changed");
        assertThat(service.project(start)).isEqualTo(before);
        assertThat(count("report_admission_delivery")).isOne();
    }
    @Test void nanosecondTime_survivesReloadAndChangedNanoIsConflict() throws Exception {
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        String exact = "2026-09-28T02:10:00.123456789Z";
        var nanos = AdmissionEvidenceTestFixtures.changed(start, start.metadata().eventId(), "admittedAt", exact);
        service.project(nanos);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            evidence.lockAdmission(start.metadata().sourceId());
            assertThat(evidence.findHistory(start.metadata().sourceId()).start().businessAt()).isEqualTo(Instant.parse(exact));
        });
        var changed = AdmissionEvidenceTestFixtures.changed(nanos, UUID.randomUUID(), "admittedAt", "2026-09-28T02:10:00.123456788Z");
        assertThatThrownBy(() -> service.project(changed)).hasMessageContaining("correction");
        assertThat(count("report_admission_delivery")).isOne();
    }
    @Test void patientMismatchAndChronologicalConflict_rollbackNewDelivery() throws Exception {
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        var close = AdmissionEvidenceTestFixtures.fixture("admission.closed");
        service.project(start);
        assertThatThrownBy(() -> service.project(AdmissionEvidenceTestFixtures.changed(close, UUID.randomUUID(), "patientId", UUID.randomUUID().toString())))
                .hasMessageContaining("patient");
        assertThatThrownBy(() -> service.project(AdmissionEvidenceTestFixtures.changed(close, UUID.randomUUID(), "closedAt", "2026-09-27T00:00:00Z")))
                .hasMessageContaining("chronological");
        assertThat(count("report_admission_delivery")).isOne();
        assertThat(count("report_admission_fact")).isOne();
        assertThat(service.project(close).status()).isEqualTo(Status.CLOSED);
    }
    @Test void callerFailure_rollsBackPendingEvidenceAndClaimThenRetryWorks() throws Exception {
        var close = AdmissionEvidenceTestFixtures.fixture("admission.closed");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.project(close);
            throw new IllegalStateException("injected caller failure");
        })).hasMessageContaining("injected");
        assertThat(count("report_admission_delivery")).isZero();
        assertThat(count("report_admission_fact")).isZero();
        assertThat(service.project(close).status()).isEqualTo(Status.PENDING_START);
    }
    @Test void concurrentStartCloseAndDuplicateStart_endClosedWithOneFactEach() throws Exception {
        var start = AdmissionEvidenceTestFixtures.fixture("admission.started");
        var close = AdmissionEvidenceTestFixtures.fixture("admission.closed");
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var a = executor.submit(() -> { assertThat(gate.await(5, TimeUnit.SECONDS)).isTrue(); return service.project(start); });
            var b = executor.submit(() -> { assertThat(gate.await(5, TimeUnit.SECONDS)).isTrue(); return service.project(close); });
            var c = executor.submit(() -> { assertThat(gate.await(5, TimeUnit.SECONDS)).isTrue(); return service.project(start); });
            gate.countDown();
            a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS); c.get(15, TimeUnit.SECONDS);
        }
        assertThat(service.project(start).status()).isEqualTo(Status.CLOSED);
        assertThat(count("report_admission_delivery")).isEqualTo(2);
        assertThat(count("report_admission_fact")).isEqualTo(2);
    }
    @Test void adapterWithoutTransaction_rejectsBeforeWrites() {
        assertThatThrownBy(() -> evidence.lockAdmission(UUID.randomUUID())).hasMessageContaining("transaction");
        assertThat(count("report_admission_target")).isZero();
    }

    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    @TestConfiguration static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean AdmissionReportFactMapper admissionReportFactMapper() { return new AdmissionReportFactMapper(); }
    }
}
