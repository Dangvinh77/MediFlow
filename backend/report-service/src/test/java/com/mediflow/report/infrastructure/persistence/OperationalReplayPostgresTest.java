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
@com.mediflow.report.support.ReportPersistenceTest
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
        jdbc.execute("TRUNCATE operational_source_snapshot, operational_replay_generation, operational_event_journal, operational_contribution, daily_operational_report CASCADE");
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

    @Test void actualClinicalPharmacySurgeryJournal_rebuildsSameMultiMetricTotalsFromEmptyGeneration() throws Exception {
        var decoder = new com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder(new ObjectMapper());
        var zone = java.time.ZoneId.of("Asia/Bangkok");
        var clinical = decoder.decode("medicalrecord.completed", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "../clinical-service/src/test/resources/contracts/medicalrecord.completed.v1.json")));
        var pharmacy = decoder.decode("prescription.filled", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.v1.json")));
        var surgery = decoder.decode("surgery.completed", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.completed.admission.v1.json")));
        live.apply(new com.mediflow.report.application.mapper.ClinicalOperationalContributionMapper(zone).map(clinical));
        live.apply(new com.mediflow.report.application.mapper.PrescriptionOperationalContributionMapper(zone).map(pharmacy));
        var surgeryMapper = new com.mediflow.report.application.mapper.SurgeryOperationalContributionMapper(zone);
        live.apply(surgeryMapper.map(surgery)); live.apply(surgeryMapper.map(surgery));
        var meta = surgery.metadata();
        live.apply(surgeryMapper.map(new DecodedCareFinanceEvent(new CareFinanceEventMetadata(UUID.randomUUID(),
                meta.eventType(), 1, meta.occurredAt(), meta.correlationId(), meta.producer(),
                meta.sourceField(), meta.sourceId()), surgery.payload())));
        var generation = replay.start();
        assertThat(generation.sourceEvents()).isEqualTo(4);
        assertThat(count("operational_replay_contribution")).isZero();
        for (int batch = 0; batch < 3; batch++) replay.advance(generation.generationId(), 1);
        assertThat(replay.advance(generation.generationId(), 1).status()).isEqualTo(Status.VERIFIED);
        assertThat(count("operational_replay_contribution")).isEqualTo(5);
        assertThat(count("operational_contribution")).isEqualTo(5);
        assertThat(jdbc.queryForList("SELECT metric_type,numeric_value FROM operational_replay_scope WHERE department_id IS NULL"))
                .hasSize(5).allSatisfy(row -> assertThat(row.get("numeric_value"))
                        .isEqualTo(row.get("metric_type").equals("SURGERY_DURATION_MINUTES") ? 6L
                                : row.get("metric_type").equals("DISPENSED_UNITS") ? 2L : 1L));
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

    @Test void read_midnightAndLeapDay_twoDepartmentsReconcileExactlyWithHospital() {
        UUID otherDepartment = UUID.randomUUID();
        Instant before = Instant.parse("2028-02-28T16:59:59.999999999Z");
        Instant after = Instant.parse("2028-02-28T17:00:00Z");
        UUID source = UUID.randomUUID();
        live.apply(datedCommand(source, department, before));
        live.apply(datedCommand(source, department, before)); // Same operation, fresh delivery ID.
        live.apply(datedCommand(UUID.randomUUID(), department, after));
        live.apply(datedCommand(UUID.randomUUID(), otherDepartment, after));
        UUID generation = replay.start().generationId();
        assertThat(replay.advance(generation, 100).status()).isEqualTo(Status.VERIFIED);
        publishTestCoverage(generation);
        jdbc.update("UPDATE operational_report_publication SET covered_from=DATE '2028-02-28',covered_to=DATE '2028-03-01'");
        LocalDate from = LocalDate.of(2028, 2, 28);
        var hospital = reader.daily(from, from.plusDays(2), null);
        var first = reader.daily(from, from.plusDays(2), department);
        var second = reader.daily(from, from.plusDays(2), otherDepartment);
        assertThat(hospital.days()).extracting(day -> day.metrics().get("completedVisits")).containsExactly(1L, 2L, 0L);
        assertThat(first.days()).extracting(day -> day.metrics().get("completedVisits")).containsExactly(1L, 1L, 0L);
        assertThat(second.days()).extracting(day -> day.metrics().get("completedVisits")).containsExactly(0L, 1L, 0L);
        for (int i = 0; i < 3; i++) {
            assertThat(hospital.days().get(i).metrics().get("completedVisits"))
                    .isEqualTo(first.days().get(i).metrics().get("completedVisits") + second.days().get(i).metrics().get("completedVisits"));
        }
        assertThat(count("operational_contribution")).isEqualTo(3);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void read_accessExclusiveLockTimesOutThenRecoversWithoutZero(boolean publicationLock) throws Exception {
        live.apply(command(UUID.randomUUID()));
        UUID generation = replay.start().generationId();
        replay.advance(generation, 100);
        publishTestCoverage(generation);
        try (var blocker = POSTGRES.createConnection("")) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.execute("LOCK TABLE " + (publicationLock ? "operational_report_publication" : "operational_replay_scope") + " IN ACCESS EXCLUSIVE MODE");
            }
            long started = System.nanoTime();
            LocalDate date = LocalDate.of(2026, 10, 1);
            assertThatThrownBy(() -> reader.daily(date, date, null))
                    .isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class);
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - started).toSeconds()).isLessThan(8);
            blocker.rollback();
        }
        LocalDate date = LocalDate.of(2026, 10, 1);
        assertThat(reader.daily(date, date, null).days().getFirst().metrics().get("completedVisits")).isOne();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void read_largeGenerationUsesCoveringIndexForDepartmentAndHospital(boolean hospital) throws Exception {
        UUID generation = replay.start().generationId();
        // Synthetic SQL volume only: not source coverage or an end-to-end producer fixture.
        jdbc.update("""
                INSERT INTO operational_replay_scope(generation_id,metric_date,department_id,metric_type,numeric_value)
                SELECT ?, DATE '2000-01-01' + d, scope.department_id, 'COMPLETED_VISITS', 1
                FROM generate_series(0,36000) d CROSS JOIN (VALUES (CAST(? AS UUID)),(CAST(? AS UUID)),(NULL::UUID)) scope(department_id)
                """, generation, department, UUID.randomUUID());
        jdbc.execute("ANALYZE operational_replay_scope");
        String scope = hospital ? "department_id IS NULL" : "department_id = '" + department + "'::UUID";
        var plan = String.join("\n", jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS) SELECT metric_date,metric_type,numeric_value"
                + " FROM operational_replay_scope WHERE generation_id = '" + generation + "'::UUID AND " + scope
                + " AND metric_date BETWEEN DATE '2028-02-28' AND DATE '2028-03-01'"
                + " AND metric_type IN ('COMPLETED_VISITS') ORDER BY metric_date,metric_type", String.class));
        // Either natural-key or covering index is valid: PostgreSQL chooses by statistics/visibility.
        // The invariant is a bounded index condition on generation, scope AND inclusive dates.
        assertThat(plan).contains("Index", "Index Cond:", "generation_id =", "department_id", "metric_date >=", "metric_date <=")
                .doesNotContain("Seq Scan", "Rows Removed by Filter");
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname='ix_operational_snapshot_read'", String.class))
                .contains("generation_id, department_id, metric_date, metric_type", "INCLUDE (numeric_value)");
        var values = new OperationalSnapshotReadPersistenceAdapter(jdbc).read(generation,
                LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 1), hospital ? null : department,
                java.util.Set.of(OperationalContribution.Metric.COMPLETED_VISITS));
        assertThat(values).hasSize(3).allSatisfy(value -> assertThat(value.value()).isOne());
    }

    private ApplyOperationalContributionCommand datedCommand(UUID source, UUID scope, Instant at) {
        UUID eventId = UUID.randomUUID();
        var event = new DecodedCareFinanceEvent(new CareFinanceEventMetadata(eventId, "medicalrecord.completed", 1,
                at, "boundary-test", "clinical-service", "recordId", source), Map.of("recordId", source.toString()));
        return new ApplyOperationalContributionCommand(event, new OperationalContribution(eventId, "MEDICAL_RECORD", source, 1,
                OperationalContribution.Metric.COMPLETED_VISITS, scope, null, null,
                at.atZone(java.time.ZoneId.of("Asia/Bangkok")).toLocalDate(), BigDecimal.ONE, null, at));
    }

    @Test void read_unknownAcceptedMetricIsUnavailableRatherThanLeakingStorageMetadata() {
        UUID generation = replay.start().generationId();
        publishTestCoverage(generation);
        jdbc.update("UPDATE operational_report_publication SET metrics=array_append(metrics,'UNSUPPORTED_PRIVATE_METRIC')");
        LocalDate date = LocalDate.of(2026, 10, 1);
        assertThatThrownBy(() -> reader.daily(date, date, null))
                .isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class)
                .hasMessageNotContaining("PRIVATE");
    }

    @Test void read_realJdbcQueryCountStaysTwoForOneDayAnd366Days() {
        UUID generation = replay.start().generationId();
        publishTestCoverage(generation);
        jdbc.update("UPDATE operational_report_publication SET covered_from=DATE '2028-01-01',covered_to=DATE '2028-12-31'");
        var queries = new java.util.concurrent.atomic.AtomicInteger();
        var dataSource = new org.springframework.jdbc.datasource.DelegatingDataSource(jdbc.getDataSource()) {
            @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
                var actual = super.getConnection();
                return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection.class.getClassLoader(),
                        new Class<?>[]{java.sql.Connection.class}, (proxy, method, args) -> {
                            if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                    && sql.stripLeading().startsWith("SELECT")) queries.incrementAndGet();
                            try { return method.invoke(actual, args); }
                            catch (java.lang.reflect.InvocationTargetException exception) { throw exception.getCause(); }
                        });
            }
        };
        // Instrument real connections/statements, not a mocked store or a substituted database.
        var measured = new OperationalSnapshotReadService(new OperationalSnapshotReadPersistenceAdapter(new JdbcTemplate(dataSource)),
                java.time.ZoneId.of("Asia/Bangkok"));
        var from = LocalDate.of(2028, 1, 1);
        assertThat(measured.daily(from, from, null).days()).hasSize(1);
        assertThat(queries.getAndSet(0)).isEqualTo(2);
        assertThat(measured.daily(from, LocalDate.of(2028, 12, 31), department).days()).hasSize(366);
        assertThat(queries.get()).isEqualTo(2);
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
