package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import com.mediflow.report.application.dto.command.carefinance.*;
import com.mediflow.report.application.dto.response.OperationalReplayProgress.Status;
import com.mediflow.report.application.service.OperationalContributionApplicationService;
import com.mediflow.report.application.service.OperationalReplayApplicationService;
import com.mediflow.report.application.service.OperationalSnapshotReadService;
import com.mediflow.report.infrastructure.persistence.adapter.OperationalSnapshotReadPersistenceAdapter;
import com.mediflow.report.domain.model.OperationalContribution;
import com.mediflow.report.infrastructure.persistence.adapter.OperationalContributionPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.OperationalReplayPersistenceAdapter;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OperationalContributionPersistenceAdapter.class, OperationalContributionApplicationService.class,
        OperationalReplayPersistenceAdapter.class, OperationalReplayApplicationService.class,
        OperationalSnapshotReadService.class, OperationalSnapshotReadPersistenceAdapter.class, OperationalReplayPostgresTest.JsonConfig.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OperationalReplayPostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired OperationalContributionApplicationService live;
    @Autowired OperationalReplayApplicationService replay;
    @Autowired OperationalReplayPersistenceAdapter store;
    @Autowired OperationalSnapshotReadService reader;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    private final UUID department = UUID.randomUUID();

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE operational_replay_generation, operational_event_journal, operational_contribution, daily_operational_report CASCADE");
    }

    @Test
    void finiteManifest_excludesLaterEventsAndDedupesReDeliveriesWithoutChangingLive() {
        UUID source = UUID.randomUUID();
        live.apply(command(source));
        live.apply(command(source));
        var generation = replay.start();
        live.apply(command(UUID.randomUUID()));
        assertThat(generation.sourceEvents()).isEqualTo(2);
        assertThat(replay.advance(generation.generationId(), 1).appliedEvents()).isOne();
        assertThat(replay.advance(generation.generationId(), 1).status()).isEqualTo(Status.VERIFIED);
        assertThat(replay.advance(generation.generationId(), 1).status()).isEqualTo(Status.VERIFIED);
        assertThat(count("operational_replay_contribution")).isOne();
        assertThat(jdbc.queryForList("SELECT numeric_value FROM operational_replay_scope", Long.class)).containsExactly(1L, 1L);
        assertThat(jdbc.queryForList("SELECT completed_visits FROM daily_operational_report", Long.class)).containsExactly(2L, 2L);
    }

    @Test
    void inFlightOldTransaction_isNotIncludedEvenWhenItCommitsAfterFreeze() throws Exception {
        var written = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                live.apply(command(UUID.randomUUID()));
                written.countDown();
                await(commit);
            }));
            assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
            var generation = replay.start();
            commit.countDown();
            future.get(20, TimeUnit.SECONDS);
            assertThat(generation.status()).isEqualTo(Status.VERIFIED);
            assertThat(generation.sourceEvents()).isZero();
            assertThat(count("operational_event_journal")).isOne();
            assertThat(count("operational_replay_input")).isZero();
        } finally { commit.countDown(); }
    }

    @Test
    void batchScopeFailure_rollsBackAndCanResume() {
        live.apply(command(UUID.randomUUID()));
        var id = replay.start().generationId();
        jdbc.execute("CREATE FUNCTION reject_replay_scope() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected replay scope failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_replay_scope BEFORE INSERT ON operational_replay_scope FOR EACH ROW WHEN (NEW.department_id IS NOT NULL) EXECUTE FUNCTION reject_replay_scope()");
        try {
            assertThatThrownBy(() -> replay.advance(id, 1)).hasMessageContaining("injected replay scope failure");
            assertThat(count("operational_replay_contribution")).isZero();
            assertThat(count("operational_replay_scope")).isZero();
            assertThat(jdbc.queryForObject("SELECT applied_events FROM operational_replay_generation WHERE generation_id = ?", Long.class, id)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_replay_scope ON operational_replay_scope");
            jdbc.execute("DROP FUNCTION reject_replay_scope()");
        }
        assertThat(replay.advance(id, 1).status()).isEqualTo(Status.VERIFIED);
    }

    @Test
    void concurrentWorkers_serializeOneGenerationAndCountEachFactOnce() throws Exception {
        live.apply(command(UUID.randomUUID()));
        live.apply(command(UUID.randomUUID()));
        var id = replay.start().generationId();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var go = new CountDownLatch(1);
            var one = executor.submit(() -> { await(go); return replay.advance(id, 1); });
            var two = executor.submit(() -> { await(go); return replay.advance(id, 1); });
            go.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        }
        assertThat(replay.advance(id, 1).status()).isEqualTo(Status.VERIFIED);
        assertThat(count("operational_replay_contribution")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT numeric_value FROM operational_replay_scope", Long.class)).containsExactly(2L, 2L);
    }

    @Test
    void corruptedFingerprint_rejectsBatchBeforeEffects() {
        live.apply(command(UUID.randomUUID()));
        var id = replay.start().generationId();
        jdbc.update("UPDATE operational_replay_input SET projection_fingerprint = ? WHERE generation_id = ?", "0".repeat(64), id);
        assertThatThrownBy(() -> replay.advance(id, 1)).hasMessageContaining("fingerprint");
        assertThat(count("operational_replay_contribution")).isZero();
    }

    @Test
    void reconciliationMismatch_failsGenerationWithoutChangingLiveProjection() {
        live.apply(command(UUID.randomUUID()));
        live.apply(command(UUID.randomUUID()));
        var id = replay.start().generationId();
        replay.advance(id, 1);
        jdbc.update("UPDATE operational_replay_scope SET numeric_value = numeric_value + 100 WHERE generation_id = ?", id);
        assertThat(replay.advance(id, 1).status()).isEqualTo(Status.FAILED);
        assertThat(jdbc.queryForList("SELECT completed_visits FROM daily_operational_report", Long.class)).containsExactly(2L, 2L);
    }

    @Test
    void separateGenerations_doNotShareDedupeAndRequireOuterTransaction() {
        assertThatThrownBy(() -> store.freeze(UUID.randomUUID())).hasMessageContaining("transaction");
        live.apply(command(UUID.randomUUID()));
        var first = replay.start().generationId();
        var second = replay.start().generationId();
        assertThat(replay.advance(first, 100).status()).isEqualTo(Status.VERIFIED);
        assertThat(replay.advance(second, 100).status()).isEqualTo(Status.VERIFIED);
        assertThat(count("operational_replay_contribution")).isEqualTo(2);
    }

    @Test void read_verifiedFiniteGenerationWithoutCoverageApproval_isUnavailable() {
        var generation = replay.start();
        assertThat(generation.status()).isEqualTo(Status.VERIFIED);
        assertThatThrownBy(() -> reader.daily(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), null))
                .isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class);
    }

    @Test void read_acceptedSnapshot_usesFrozenGenerationNotLaterLiveFactsAndZeroFills() {
        live.apply(command(UUID.randomUUID()));
        UUID id = replay.start().generationId();
        assertThat(replay.advance(id, 100).status()).isEqualTo(Status.VERIFIED);
        publishTestCoverage(id);
        live.apply(command(UUID.randomUUID()));
        var from = LocalDate.of(2026, 10, 1);
        var result = reader.daily(from, from.plusDays(1), null);
        assertThat(result.days().getFirst().metrics().get("completedVisits")).isOne();
        assertThat(result.days().get(1).metrics().values()).containsOnly(0L);
        assertThat(result.snapshotOnly()).isTrue();
        assertThat(reader.daily(from, from, department).days().getFirst().metrics().get("completedVisits")).isOne();
        assertThat(reader.daily(from, from, UUID.randomUUID()).days().getFirst().metrics().values()).containsOnly(0L);
    }

    @Test void read_buildingOrFailedGeneration_neverPublishesSuccessfulZeros() {
        live.apply(command(UUID.randomUUID()));
        UUID id = replay.start().generationId();
        publishTestCoverage(id);
        var date = LocalDate.of(2026, 10, 1);
        assertThatThrownBy(() -> reader.daily(date, date, null)).isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class);
        jdbc.update("UPDATE operational_replay_generation SET status = 'FAILED', completed_at = now() WHERE generation_id = ?", id);
        assertThatThrownBy(() -> reader.daily(date, date, null)).isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class);
    }

    @Test void read_coveragePartialOrTimezoneMismatch_deniesWithoutInventingMetrics() {
        var generation = replay.start();
        publishTestCoverage(generation.generationId());
        var date = LocalDate.of(2026, 10, 1);
        jdbc.update("UPDATE operational_report_publication SET metrics = ARRAY['COMPLETED_VISITS']");
        assertThatThrownBy(() -> reader.daily(date, date, null)).isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class);
        jdbc.update("DELETE FROM operational_report_publication");
        publishTestCoverage(generation.generationId());
        jdbc.update("UPDATE operational_report_publication SET zone_id = 'UTC'");
        assertThatThrownBy(() -> reader.daily(date, date, null)).isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class);
    }

    private void publishTestCoverage(UUID generationId) {
        // Test-only accepted manifest. NOT producer-owner sign-off or a production activation path.
        jdbc.update("""
                INSERT INTO operational_report_publication(report_kind, generation_id, covered_from, covered_to, zone_id, metrics, acceptance_reference)
                VALUES ('DAILY', ?, DATE '2026-10-01', DATE '2026-10-02', 'Asia/Bangkok',
                  ARRAY['COMPLETED_VISITS','ADMISSIONS','DISCHARGES','LAB_TESTS','DISPENSED_PRESCRIPTIONS','DISPENSED_UNITS'], 'TEST-ONLY-ACCEPTANCE')
                """, generationId);
    }

    private ApplyOperationalContributionCommand command(UUID source) {
        UUID event = UUID.randomUUID();
        Instant time = Instant.parse("2026-10-01T08:00:00.123456789Z");
        return new ApplyOperationalContributionCommand(new DecodedCareFinanceEvent(new CareFinanceEventMetadata(event,
                "medicalrecord.completed", 1, time, "trace", "clinical-service", "recordId", source), Map.of()),
                new OperationalContribution(event, "MEDICAL_RECORD", source, 1, OperationalContribution.Metric.COMPLETED_VISITS,
                        department, null, null, LocalDate.of(2026, 10, 1), BigDecimal.ONE, null, time));
    }

    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Latch timed out"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
    @TestConfiguration static class JsonConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean java.time.ZoneId reportZoneId() { return java.time.ZoneId.of("Asia/Bangkok"); }
    }
}
