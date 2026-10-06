package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.port.in.ExpireSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase.Candidate;
import com.mediflow.surgery.application.port.in.RecordSurgeryReadinessExpiryRetryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "mediflow.jwt.secret=surgery-expiry-secret-at-least-32-bytes",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "mediflow.features.surgery.enabled=true", "mediflow.surgery.readiness.expiry.enabled=false"
})
class SurgeryReadinessExpiryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-05T08:00:00Z");
    private static final SurgeryAuditActor ACTOR = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
    @Container private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("surgery_expiry").withUsername("surgery").withPassword("surgery");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired private SurgeryCaseRepositoryPort cases;
    @Autowired private SurgeryScheduleRepositoryPort schedules;
    @Autowired private SurgeryResourceReservationPort resources;
    @Autowired private QueryExpiredSurgeryReadinessUseCase query;
    @Autowired private ExpireSurgeryReadinessUseCase expire;
    @Autowired private RecordSurgeryReadinessExpiryRetryUseCase retry;
    @Autowired private com.mediflow.surgery.application.port.in.CancelSurgeryUseCase cancel;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private SurgeryClockPort clock;

    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE surgery_case CASCADE");
        when(clock.now()).thenReturn(NOW);
    }

    @Test void expire_readyCaseAtExactDeadlinePreservesEvidenceAndAuditsSystemActor() {
        var fixture = persist(false, NOW);
        assertThat(query.findDue(20)).containsExactly(fixture.candidate());
        assertThat(expire.expire(fixture.candidate(), "expiry-pg")).isTrue();
        var stored = cases.findById(fixture.value().getSurgeryCaseId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(stored.getRevision()).isEqualTo(3);
        assertThat(stored.getReadinessSnapshot()).isNull();
        assertThat(count("surgery_readiness_snapshot", fixture.value().getSurgeryCaseId())).isEqualTo(1);
        assertThat(count("surgery_outbox", fixture.value().getSurgeryCaseId())).isZero();
        assertThat(jdbc.queryForMap("SELECT actor_type, account_id, system_producer FROM surgery_status_history "
                + "WHERE surgery_case_id = ? AND reason = 'READINESS_EXPIRED'", fixture.value().getSurgeryCaseId()))
                .containsEntry("actor_type", "SYSTEM").containsEntry("account_id", null)
                .containsEntry("system_producer", "surgery-service");
        assertThat(query.findDue(20)).isEmpty();
        assertThat(expire.expire(fixture.candidate(), "expiry-replay")).isFalse();
    }

    @Test void expire_scheduledCaseReleasesExactBookingAndKeepsOtherCaseUntouched() {
        var due = persist(true, NOW);
        var other = persist(true, NOW.plusSeconds(60));
        assertThat(expire.expire(due.candidate(), "expiry-pg")).isTrue();
        assertResources(due, "RELEASED");
        assertResources(other, "RESERVED");
        assertThat(cases.findById(other.value().getSurgeryCaseId()).orElseThrow().getStatus()).isEqualTo(SurgeryStatus.SCHEDULED);
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_schedule WHERE schedule_id = ?", String.class,
                due.schedule().scheduleId())).isEqualTo("RELEASED");
    }

    @Test void expire_beforeDeadlineAndMissingValidityDoNotMutate() {
        var future = persist(true, NOW.plusSeconds(1));
        var timeless = persist(false, null);
        assertThat(query.findDue(20)).isEmpty();
        assertThat(expire.expire(future.candidate(), "expiry-pg")).isFalse();
        assertThat(expire.expire(timeless.candidate(), "expiry-pg")).isFalse();
        assertResources(future, "RESERVED");
        when(clock.now()).thenReturn(NOW.plusSeconds(1));
        assertThat(query.findDue(20)).containsExactly(future.candidate());
    }

    @Test void expire_historyFailureRollsBackStateAndReleaseThenDurableRetryRecovers() {
        var fixture = persist(true, NOW);
        jdbc.execute("""
                CREATE FUNCTION fail_readiness_expiry() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.reason = 'READINESS_EXPIRED' THEN RAISE EXCEPTION 'test audit failure'; END IF;
                RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER fail_readiness_expiry BEFORE INSERT ON surgery_status_history "
                + "FOR EACH ROW EXECUTE FUNCTION fail_readiness_expiry()");
        try {
            assertThatThrownBy(() -> expire.expire(fixture.candidate(), "expiry-pg")).isInstanceOf(RuntimeException.class);
            var stored = cases.findById(fixture.value().getSurgeryCaseId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(SurgeryStatus.SCHEDULED);
            assertThat(stored.getRevision()).isEqualTo(3);
            assertThat(stored.getReadinessSnapshot().snapshotId()).isEqualTo(fixture.candidate().readinessSnapshotId());
            assertResources(fixture, "RESERVED");
            retry.defer(fixture.candidate(), "AuditWriteFailed");
            assertThat(query.findDue(20)).isEmpty();
            assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM surgery_readiness_expiry_retry WHERE surgery_case_id = ?",
                    Timestamp.class, fixture.value().getSurgeryCaseId()).toInstant()).isEqualTo(NOW.plusSeconds(5));
        } finally {
            jdbc.execute("DROP TRIGGER fail_readiness_expiry ON surgery_status_history");
            jdbc.execute("DROP FUNCTION fail_readiness_expiry()");
        }
        when(clock.now()).thenReturn(NOW.plusSeconds(5));
        assertThat(query.findDue(20)).containsExactly(fixture.candidate());
        assertThat(expire.expire(fixture.candidate(), "expiry-recovered")).isTrue();
        assertResources(fixture, "RELEASED");
        assertThat(query.findDue(20)).isEmpty();
    }

    @Test void expire_twoWorkersCommitExactlyOneInvalidation() throws Exception {
        var fixture = persist(true, NOW);
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return expire.expire(fixture.candidate(), "expiry-a"); });
            var second = workers.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return expire.expire(fixture.candidate(), "expiry-b"); });
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_status_history WHERE surgery_case_id = ? "
                + "AND reason = 'READINESS_EXPIRED'", Integer.class, fixture.value().getSurgeryCaseId())).isEqualTo(1);
        assertResources(fixture, "RELEASED");
    }

    @Test void expire_committedStartWinnerRetainsInUseAndReceivesNoRetry() {
        var fixture = persist(true, NOW);
        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> {
            var value = cases.lockById(fixture.value().getSurgeryCaseId()).orElseThrow();
            resources.markInUse(value.getSurgeryCaseId(), fixture.schedule().scheduleId(), 1, NOW.minusSeconds(5));
            value.start(value.getReadinessSnapshot(), ACTOR, "start-pg", NOW.minusSeconds(5));
            cases.save(value, 3);
        });
        assertThat(query.findDue(20)).isEmpty();
        assertThat(expire.expire(fixture.candidate(), "expiry-pg")).isFalse();
        retry.defer(fixture.candidate(), "StaleCandidate");
        assertResources(fixture, "IN_USE");
        assertThat(count("surgery_readiness_expiry_retry", fixture.value().getSurgeryCaseId())).isZero();
    }

    @Test void retry_capsBackoffAndDoesNotStarveOtherDueCase() {
        var failed = persist(true, NOW);
        var healthy = persist(false, NOW);
        for (int attempt = 0; attempt < 24; attempt++) retry.defer(failed.candidate(), "TransientFailure");
        assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM surgery_readiness_expiry_retry WHERE surgery_case_id = ?",
                Timestamp.class, failed.value().getSurgeryCaseId()).toInstant()).isEqualTo(NOW.plusSeconds(300));
        assertThat(query.findDue(1)).containsExactly(healthy.candidate());
        assertThat(expire.expire(healthy.candidate(), "expiry-healthy")).isTrue();
        assertResources(failed, "RESERVED");
    }

    @Test void expire_committedCancellationWinnerIsNeverReopened() {
        var fixture = persist(true, NOW);
        cancel.cancel(cancelCommand(fixture));
        assertThat(expire.expire(fixture.candidate(), "expiry-after-cancel")).isFalse();
        retry.defer(fixture.candidate(), "StaleCandidate");
        assertThat(query.findDue(20)).isEmpty();
        assertThat(cases.findById(fixture.value().getSurgeryCaseId()).orElseThrow().getStatus()).isEqualTo(SurgeryStatus.CANCELLED);
        assertResources(fixture, "RELEASED");
        assertThat(count("surgery_readiness_expiry_retry", fixture.value().getSurgeryCaseId())).isZero();
    }

    @Test void expire_concurrentCancellationHasOneCoherentWinner() throws Exception {
        var fixture = persist(true, NOW);
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var expiration = workers.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return expire.expire(fixture.candidate(), "expiry-race");
            });
            var cancellation = workers.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { cancel.cancel(cancelCommand(fixture)); return true; }
                catch (com.mediflow.surgery.application.exception.SurgeryRevisionConflictException expected) { return false; }
            });
            boolean expired = expiration.get(15, TimeUnit.SECONDS);
            boolean cancelled = cancellation.get(15, TimeUnit.SECONDS);
            assertThat(expired).isNotEqualTo(cancelled);
            var stored = cases.findById(fixture.value().getSurgeryCaseId()).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(cancelled ? SurgeryStatus.CANCELLED : SurgeryStatus.PREOP_IN_PROGRESS);
            assertThat(stored.getRevision()).isEqualTo(4);
            assertThat(stored.getReadinessSnapshot()).isNull();
            assertThat(count("surgery_command_receipt", fixture.value().getSurgeryCaseId())).isEqualTo(cancelled ? 1 : 0);
            assertResources(fixture, "RELEASED");
        }
    }

    @Test void expire_lockedCaseTimesOutWithoutMutationAndCanRecover() throws Exception {
        var fixture = persist(true, NOW);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holder = workers.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                cases.lockById(fixture.value().getSurgeryCaseId()).orElseThrow();
                locked.countDown();
                try { if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test lock wait exceeded"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var waiting = workers.submit(() -> expire.expire(fixture.candidate(), "expiry-lock-wait"));
            try {
                assertThatThrownBy(() -> waiting.get(8, TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class);
            } finally { release.countDown(); }
            holder.get(5, TimeUnit.SECONDS);
        }
        assertResources(fixture, "RESERVED");
        assertThat(cases.findById(fixture.value().getSurgeryCaseId()).orElseThrow().getStatus()).isEqualTo(SurgeryStatus.SCHEDULED);
        retry.defer(fixture.candidate(), "LockTimeout");
        when(clock.now()).thenReturn(NOW.plusSeconds(5));
        assertThat(expire.expire(fixture.candidate(), "expiry-lock-recovery")).isTrue();
        assertResources(fixture, "RELEASED");
    }

    private com.mediflow.surgery.application.port.in.CancelSurgeryUseCase.Command cancelCommand(Fixture fixture) {
        return new com.mediflow.surgery.application.port.in.CancelSurgeryUseCase.Command(
                fixture.value().getSurgeryCaseId(), 3, "TEST_CANCELLED", "expiry-cancel-key",
                new com.mediflow.surgery.application.dto.SurgeryActorIdentity(ACTOR.accountId(), ACTOR.verifiedStaffId()), "expiry-cancel");
    }

    @Test void expire_andRetryFromOldSnapshotDoNotAffectReplacement() {
        var fixture = persist(false, NOW);
        retry.defer(fixture.candidate(), "TransientFailure");
        var tx = new TransactionTemplate(transactionManager);
        var replacement = tx.execute(ignored -> {
            var value = cases.lockById(fixture.value().getSurgeryCaseId()).orElseThrow();
            value.invalidateReadiness(ACTOR, "replace-pg", NOW.minusSeconds(25), "TEST_REPLACEMENT");
            var next = snapshot(value, fixture.schedule(), NOW.minusSeconds(20), NOW);
            value.markReady(next, ACTOR, "replace-pg");
            cases.save(value, 2);
            return new Candidate(value.getSurgeryCaseId(), next.snapshotId());
        });
        assertThat(expire.expire(fixture.candidate(), "expiry-old")).isFalse();
        retry.defer(fixture.candidate(), "StaleCandidate");
        assertThat(query.findDue(20)).containsExactly(replacement);
        assertThat(expire.expire(replacement, "expiry-new")).isTrue();
        assertThat(count("surgery_readiness_snapshot", fixture.value().getSurgeryCaseId())).isEqualTo(2);
    }

    private Fixture persist(boolean scheduled, Instant validUntil) {
        var tx = new TransactionTemplate(transactionManager);
        return tx.execute(ignored -> {
            var value = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                    new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                    UUID.randomUUID(), UUID.randomUUID(), ACTOR.verifiedStaffId(), "EXPIRY-TEST", "Test indication",
                    SurgeryPriority.ROUTINE, NOW.minusSeconds(60), ACTOR, "expiry-pg");
            cases.save(value, -1);
            value.beginPreop(ACTOR, "expiry-pg", NOW.minusSeconds(50));
            cases.save(value, 0);
            var schedule = new SurgerySchedule(UUID.randomUUID(), value.getSurgeryCaseId(), 1, UUID.randomUUID(),
                    NOW.plusSeconds(100), NOW.plusSeconds(200),
                    List.of(new SurgeryTeamAssignment(UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON)));
            schedules.saveDraft(schedule, 0, NOW.minusSeconds(40));
            var readiness = snapshot(value, schedule, NOW.minusSeconds(30), validUntil);
            value.markReady(readiness, ACTOR, "expiry-pg");
            cases.save(value, 1);
            if (scheduled) {
                resources.reserve(schedule, NOW.minusSeconds(20));
                value.finalizeSchedule(ACTOR, "expiry-pg", NOW.minusSeconds(20));
                cases.save(value, 2);
            }
            return new Fixture(value, schedule, new Candidate(value.getSurgeryCaseId(), readiness.snapshotId()));
        });
    }

    // Isolated expiry prerequisites only, not an approved clinical policy or end-to-end readiness decision.
    private ReadinessSnapshot snapshot(SurgeryCase value, SurgerySchedule schedule, Instant evaluated, Instant validUntil) {
        return ReadinessSnapshot.evaluate(UUID.randomUUID(), value.getSurgeryCaseId(), true, true, true, true, true, true, true,
                evaluated, Arrays.stream(SurgeryDependencyType.values()).map(type -> type == SurgeryDependencyType.SCHEDULE
                        ? new SurgeryDependencyRevision(type, schedule.scheduleId(), schedule.revision())
                        : new SurgeryDependencyRevision(type, UUID.randomUUID(), 1)).toList(), validUntil);
    }
    private void assertResources(Fixture fixture, String status) {
        assertThat(jdbc.queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id = ?",
                String.class, fixture.value().getSurgeryCaseId())).containsExactly(status, status);
    }
    private int count(String table, UUID caseId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE surgery_case_id = ?", Integer.class, caseId);
    }
    private record Fixture(SurgeryCase value, SurgerySchedule schedule, Candidate candidate) { }
}
