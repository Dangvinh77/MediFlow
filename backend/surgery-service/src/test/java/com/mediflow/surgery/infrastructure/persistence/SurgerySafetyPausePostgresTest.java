package com.mediflow.surgery.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.domain.model.*;
import com.mediflow.surgery.support.SurgeryScheduledFixture;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.mediflow.surgery.support.SurgerySafetyPauseFixture.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class SurgerySafetyPausePostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate db;
    static TransactionTemplate tx;
    static SurgerySafetyPausePersistenceAdapter store;

    @BeforeAll static void migrate() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()).load().migrate();
        var source = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        db = new JdbcTemplate(source);
        // Empty-ledger cleanup preserves compatibility; populated-ledger denial is tested below.
        db.execute("TRUNCATE surgery_safety_pause");
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setTimeout(5);
        store = new SurgerySafetyPausePersistenceAdapter(db, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test void append_withoutTransaction_rejects() {
        var fixture = seed();
        assertThatThrownBy(() -> store.append(first(fixture))).isInstanceOf(IllegalStateException.class);
        assertThat(count(fixture)).isZero();
    }

    @Test void append_preservesNanosecondsPolicyAndOriginalReceiptAcrossReconstruction() {
        var fixture = seed();
        var original = first(fixture);
        assertThat(tx.<SurgerySafetyPause>execute(status -> store.append(original))).isEqualTo(original);
        var restarted = new SurgerySafetyPausePersistenceAdapter(db, new ObjectMapper().registerModule(new JavaTimeModule()));
        var retry = copy(original, UUID.randomUUID(), original.commandId(), original.policy(), original.phase(),
                original.observedAt(), original.recordedAt().plusSeconds(60), original.decisions());
        assertThat(tx.<SurgerySafetyPause>execute(status -> restarted.append(retry))).isEqualTo(original);
        assertThat(count(fixture)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT observed_at_iso FROM surgery_safety_pause WHERE surgery_case_id=?",
                String.class, fixture.caseId())).isEqualTo(original.observedAt().toString());
        assertThat(db.queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id=?", String.class,
                fixture.caseId())).isEqualTo("SCHEDULED");
        assertThat(db.queryForObject("SELECT count(*) FROM surgery_care_event_outbox WHERE surgery_case_id=?",
                Integer.class, fixture.caseId())).isZero();
    }

    @Test void append_changedObservedIntent_conflictsWithoutReplacingEvidence() {
        var fixture = seed();
        var original = first(fixture);
        tx.execute(status -> store.append(original));
        var changed = copy(original, UUID.randomUUID(), original.commandId(), policy(), original.phase(),
                original.observedAt().plusNanos(1), original.recordedAt(), original.decisions());
        assertThatThrownBy(() -> tx.execute(status -> store.append(changed))).isInstanceOf(SurgeryRevisionConflictException.class);
        assertThat(count(fixture)).isEqualTo(1);
    }

    @Test void append_samePhaseNewCommand_conflicts() {
        var fixture = seed();
        var original = first(fixture);
        tx.execute(status -> store.append(original));
        var changed = copy(original, UUID.randomUUID(), UUID.randomUUID(), policy(), original.phase(),
                original.observedAt(), original.recordedAt(), original.decisions());
        assertThatThrownBy(() -> tx.execute(status -> store.append(changed))).isInstanceOf(SurgeryRevisionConflictException.class);
        assertThat(count(fixture)).isEqualTo(1);
    }

    @Test void append_missingPreviousPhase_rejectsWithoutEvidence() {
        var fixture = seed();
        started(fixture);
        var incision = pause(fixture.caseId(), fixture.scheduleId(), SurgerySafetyPhase.BEFORE_INCISION, 4);
        assertThatThrownBy(() -> tx.execute(status -> store.append(incision))).isInstanceOf(SurgeryRuleException.class);
        assertThat(count(fixture)).isZero();
    }

    @Test void append_fullOrderedObservations_neverCompletesCaseOrPublishesCharges() {
        var fixture = seed();
        tx.execute(status -> store.append(first(fixture)));
        started(fixture);
        tx.execute(status -> store.append(pause(fixture.caseId(), fixture.scheduleId(), SurgerySafetyPhase.BEFORE_INCISION, 4)));
        tx.execute(status -> store.append(pause(fixture.caseId(), fixture.scheduleId(), SurgerySafetyPhase.BEFORE_OR_EXIT, 4)));
        assertThat(count(fixture)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id=?", String.class,
                fixture.caseId())).isEqualTo("IN_PROGRESS");
        assertThat(db.queryForObject("SELECT count(*) FROM surgery_result WHERE surgery_case_id=?", Integer.class,
                fixture.caseId())).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM surgery_care_event_outbox WHERE surgery_case_id=?", Integer.class,
                fixture.caseId())).isZero();
    }

    @Test void append_changedPolicyAcrossPhases_rejectsPinnedEvidenceReplacement() {
        var fixture = seed();
        tx.execute(status -> store.append(first(fixture)));
        started(fixture);
        var second = pause(fixture.caseId(), fixture.scheduleId(), SurgerySafetyPhase.BEFORE_INCISION, 4);
        var changed = new SurgerySafetyPausePolicy(policy().policyId(), 2, "TEST-PROC", UUID.randomUUID(), policy().phases());
        var value = copy(second, second.pauseId(), second.commandId(), changed, second.phase(), second.observedAt(),
                second.recordedAt(), second.decisions());
        assertThatThrownBy(() -> tx.execute(status -> store.append(value))).isInstanceOf(SurgeryRuleException.class);
        assertThat(count(fixture)).isEqualTo(1);
    }

    @Test void append_writeFailure_rollsBackAndSameCommandRecovers() {
        var fixture = seed();
        var value = first(fixture);
        assertThatThrownBy(() -> tx.execute(status -> {
            store.append(value);
            throw new IllegalStateException("Injected after append");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count(fixture)).isZero();
        assertThat(tx.<SurgerySafetyPause>execute(status -> store.append(value))).isEqualTo(value);
        assertThat(count(fixture)).isEqualTo(1);
    }

    @Test void append_concurrentSameCommand_returnsSingleOriginalReceipt() throws Exception {
        var fixture = seed();
        var value = first(fixture);
        var retry = copy(value, UUID.randomUUID(), value.commandId(), policy(), value.phase(), value.observedAt(),
                value.recordedAt().plusSeconds(60), value.decisions());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            var one = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return tx.execute(status -> store.append(value)); });
            var two = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return tx.execute(status -> store.append(retry)); });
            assertThat(one.get(10, TimeUnit.SECONDS)).isEqualTo(two.get(10, TimeUnit.SECONDS));
        }
        assertThat(count(fixture)).isEqualTo(1);
    }

    @Test void evidence_updateDeleteAndTruncate_areBlockedByDatabase() {
        var fixture = seed();
        tx.execute(status -> store.append(first(fixture)));
        assertThatThrownBy(() -> db.update("UPDATE surgery_safety_pause SET phase='BEFORE_OR_EXIT' WHERE surgery_case_id=?",
                fixture.caseId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> db.update("DELETE FROM surgery_safety_pause WHERE surgery_case_id=?", fixture.caseId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> db.execute("TRUNCATE surgery_safety_pause")).isInstanceOf(DataAccessException.class);
        assertThat(count(fixture)).isEqualTo(1);
    }

    @Test void append_oldScheduleRevisionAndStaleCaseRevision_reject() {
        var fixture = seed();
        var value = first(fixture);
        db.update("UPDATE surgery_schedule SET revision=2 WHERE surgery_case_id=?", fixture.caseId());
        assertThatThrownBy(() -> tx.execute(status -> store.append(value))).isInstanceOf(SurgeryRuleException.class);
        db.update("UPDATE surgery_schedule SET revision=1 WHERE surgery_case_id=?", fixture.caseId());
        db.update("UPDATE surgery_case SET revision=4 WHERE surgery_case_id=?", fixture.caseId());
        assertThatThrownBy(() -> tx.execute(status -> store.append(value))).isInstanceOf(SurgeryRevisionConflictException.class);
        assertThat(count(fixture)).isZero();
    }

    private static SurgeryScheduledFixture.Fixture seed() {
        return SurgeryScheduledFixture.seed(db, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(600), Instant.now().plusSeconds(1200));
    }
    private static SurgerySafetyPause first(SurgeryScheduledFixture.Fixture fixture) {
        return pause(fixture.caseId(), fixture.scheduleId(), SurgerySafetyPhase.BEFORE_ANAESTHESIA, 3);
    }
    private static int count(SurgeryScheduledFixture.Fixture fixture) {
        return db.queryForObject("SELECT count(*) FROM surgery_safety_pause WHERE surgery_case_id=?", Integer.class, fixture.caseId());
    }
    private static void started(SurgeryScheduledFixture.Fixture fixture) {
        // Local test prerequisite only: phase recording never performs this mutation.
        db.update("UPDATE surgery_case SET status='IN_PROGRESS',revision=4,started_at=now() WHERE surgery_case_id=?", fixture.caseId());
        db.update("UPDATE surgery_schedule SET status='IN_USE' WHERE surgery_case_id=?", fixture.caseId());
    }
}
