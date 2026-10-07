package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.report.application.service.ReportTelemetryApplicationService;
import com.mediflow.report.infrastructure.persistence.adapter.ReportTelemetryPersistenceAdapter;

/** Diagnostic SQL tests use synthetic local rows, not producer/consumer workflow acceptance. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReportTelemetryApplicationService.class, ReportTelemetryPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReportTelemetryPostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired ReportTelemetryApplicationService reader;
    @Autowired ReportTelemetryPersistenceAdapter store;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE report_admission_target, operational_source_snapshot, operational_replay_generation, cash_replay_generation CASCADE");
    }

    @Test
    void emptySnapshot_hasZeroLocalBacklogButDoesNotPublishCoverage() {
        var snapshot = reader.capture();
        assertThat(snapshot.sampledAt()).isBeforeOrEqualTo(Instant.now()).isAfter(Instant.now().minusSeconds(10));
        assertThat(snapshot.pendingAdmissions()).isZero();
        assertThat(snapshot.oldestPendingObservedAt()).isNull();
        assertThat(snapshot.operational().remainingInputs()).isZero();
        assertThat(snapshot.cash().building()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_report_publication", Long.class)).isZero();
    }

    @Test
    void closeBeforeStart_countsOnlyUnpairedCloseAndAgeUsesObservedTimeNotClinicalDate() {
        Instant observed = Instant.parse("2026-10-07T02:00:00Z");
        UUID missing = UUID.randomUUID();
        close(missing, observed);
        UUID paired = UUID.randomUUID();
        close(paired, observed.minusSeconds(3600)); start(paired);
        var snapshot = reader.capture();
        assertThat(snapshot.pendingAdmissions()).isOne();
        assertThat(snapshot.oldestPendingObservedAt()).isEqualTo(observed);
        start(missing);
        assertThat(reader.capture().pendingAdmissions()).isZero();
        assertThat(reader.capture().oldestPendingObservedAt()).isNull();
    }

    @Test
    void replayCounts_separateKindsAndTerminalCountsDoNotPolluteActiveProgress() {
        Instant oldest = Instant.parse("2026-10-07T00:00:00Z");
        generation("operational", "BUILDING", 10, 2, oldest);
        generation("operational", "BUILDING", 8, 7, oldest.plusSeconds(100));
        generation("operational", "VERIFIED", 100, 100, oldest.minusSeconds(10));
        generation("operational", "FAILED", 80, 1, oldest.minusSeconds(20));
        generation("cash", "BUILDING", 5, 3, oldest.plusSeconds(1000));
        var snapshot = reader.capture();
        assertThat(snapshot.operational().building()).isEqualTo(2);
        assertThat(snapshot.operational().verified()).isOne();
        assertThat(snapshot.operational().failed()).isOne();
        assertThat(snapshot.operational().sourceInputs()).isEqualTo(18);
        assertThat(snapshot.operational().appliedInputs()).isEqualTo(9);
        assertThat(snapshot.operational().remainingInputs()).isEqualTo(9);
        assertThat(snapshot.operational().oldestBuildingAt()).isEqualTo(oldest);
        assertThat(snapshot.cash().remainingInputs()).isEqualTo(2);
        assertThat(snapshot.cash().failed()).isZero();
    }

    @Test
    void legacyUnverified_countsOnlyUnverifiedHashEvidenceNotEverySource() {
        source("LEGACY_UNVERIFIED", null); source("VERIFIED_PAYLOAD", "a".repeat(64));
        assertThat(reader.capture().legacyUnverifiedSources()).isOne();
    }

    @Test
    void uncommittedCompletion_snapshotSeesCommittedStateAndMovesAtomicallyAfterCommit() throws Exception {
        UUID id = generation("cash", "BUILDING", 10, 2, Instant.now().minusSeconds(300));
        try (var writer = POSTGRES.createConnection("")) {
            writer.setAutoCommit(false);
            try (var statement = writer.prepareStatement("UPDATE cash_replay_generation SET status='VERIFIED', applied_receipts=10, completed_at=now() WHERE generation_id=?")) {
                statement.setObject(1, id); statement.executeUpdate();
            }
            var before = reader.capture().cash();
            assertThat(before.building()).isOne();
            assertThat(before.verified()).isZero();
            assertThat(before.remainingInputs()).isEqualTo(8);
            writer.commit();
        }
        var after = reader.capture().cash();
        assertThat(after.building()).isZero();
        assertThat(after.verified()).isOne();
        assertThat(after.sourceInputs()).isZero();
        assertThat(after.oldestBuildingAt()).isNull();
    }

    @Test
    void totalProgressOverflow_isUnavailableRatherThanTruncatedOrWrapped() {
        generation("cash", "BUILDING", Long.MAX_VALUE, 0, Instant.now());
        generation("cash", "BUILDING", Long.MAX_VALUE, 0, Instant.now());
        assertThatThrownBy(reader::capture).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void lockContention_cancelsQueryWithinBudgetAndNextReadRecovers() throws Exception {
        try (var writer = POSTGRES.createConnection("")) {
            writer.setAutoCommit(false);
            try (var statement = writer.createStatement()) {
                statement.execute("LOCK TABLE report_admission_fact IN ACCESS EXCLUSIVE MODE");
            }
            long started = System.nanoTime();
            assertThatThrownBy(reader::capture).isInstanceOf(DataAccessException.class);
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - started).toSeconds()).isLessThan(8);
            writer.rollback();
        }
        assertThat(reader.capture().pendingAdmissions()).isZero();
    }

    @Test
    void adapter_requiresTransactionAndReadOnlyUseCaseDoesNotAlterSourceData() {
        close(UUID.randomUUID(), Instant.now());
        String before = jdbc.queryForObject("SELECT row_to_json(f)::text FROM report_admission_fact f", String.class);
        assertThatThrownBy(store::capture).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        reader.capture(); reader.capture();
        assertThat(jdbc.queryForObject("SELECT row_to_json(f)::text FROM report_admission_fact f", String.class)).isEqualTo(before);
        var tx = new TransactionTemplate(manager); tx.setReadOnly(true);
        String readOnly = tx.execute(status -> jdbc.queryForObject("SHOW transaction_read_only", String.class));
        assertThat(readOnly).isEqualTo("on");
    }

    private void close(UUID admission, Instant observed) {
        jdbc.update("INSERT INTO report_admission_target(admission_id) VALUES (?)", admission);
        jdbc.update("""
                INSERT INTO report_admission_fact(admission_id,fact_type,patient_id,business_at,business_at_iso,
                    emergency,settlement_id,created_at)
                VALUES (?,'CLOSED',?,TIMESTAMPTZ '2000-01-01T00:00:00Z','2000-01-01T00:00:00Z',false,?,?)
                """, admission, UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(observed));
    }

    private void start(UUID admission) {
        jdbc.update("""
                INSERT INTO report_admission_fact(admission_id,fact_type,patient_id,department_id,bed_id,
                    business_at,business_at_iso,emergency)
                VALUES (?,'STARTED',?,?,?,TIMESTAMPTZ '1999-12-31T00:00:00Z','1999-12-31T00:00:00Z',false)
                """, admission, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private UUID generation(String kind, String status, long source, long applied, Instant at) {
        UUID id = UUID.randomUUID();
        String table = kind.equals("cash") ? "cash_replay_generation" : "operational_replay_generation";
        String suffix = kind.equals("cash") ? "receipts" : "events";
        jdbc.update("INSERT INTO " + table + "(generation_id,status,source_" + suffix + ",applied_" + suffix
                + ",created_at,completed_at) VALUES (?,?,?,?,?,?)", id, status, source, applied, Timestamp.from(at),
                status.equals("BUILDING") ? null : Timestamp.from(at.plusSeconds(1)));
        return id;
    }

    private void source(String state, String fingerprint) {
        jdbc.update("""
                INSERT INTO operational_source_snapshot(source_type,source_id,source_revision,event_type,
                    first_event_id,payload_fingerprint,evidence_state) VALUES ('LAB_RESULT',?,1,'lab.result.created',?,?,?)
                """, UUID.randomUUID(), UUID.randomUUID(), fingerprint, state);
    }
}
