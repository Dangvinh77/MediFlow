package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.application.exception.SurgeryScheduleConflictException;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "mediflow.jwt.secret=surgery-integration-secret-at-least-32-bytes",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class SurgeryCasePersistenceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("surgery_case")
            .withUsername("surgery")
            .withPassword("surgery");

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-28T08:00:00Z");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private SurgeryCaseRepositoryPort cases;
    @Autowired private SurgeryScheduleRepositoryPort schedules;
    @Autowired private SurgeryResourceReservationPort resources;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void migrationValidatesJpaAndRoundTripsCaseWithAppendOnlyHistory() {
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        UUID caseId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        SurgeryCase created = SurgeryCase.create(caseId, requestId,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PROC-001",
                "Clinical indication", SurgeryPriority.ROUTINE, REQUESTED_AT, actor, "case-db-test");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> cases.save(created, -1));

        SurgeryCase loaded = tx.execute(ignored -> cases.findByRequestId(requestId).orElseThrow());
        assertThat(loaded.getSurgeryCaseId()).isEqualTo(caseId);
        assertThat(loaded.getStatus()).isEqualTo(SurgeryStatus.REQUESTED);
        assertThat(loaded.getRevision()).isZero();
        assertThat(loaded.getStatusHistory()).hasSize(1);
        assertThat(loaded.getRevisionHistory()).hasSize(1);

        loaded.beginPreop(actor, "case-db-test", REQUESTED_AT.plusSeconds(60));
        tx.executeWithoutResult(ignored -> cases.save(loaded, 0));
        SurgeryCase again = tx.execute(ignored -> cases.lockById(caseId).orElseThrow());
        assertThat(again.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(again.getRevision()).isEqualTo(1);
        assertThat(again.getStatusHistory()).hasSize(2);
        assertThat(again.getRevisionHistory()).hasSize(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_revision_history WHERE surgery_case_id = ?
                """, Integer.class, caseId)).isEqualTo(2);
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> cases.save(loaded, 0)))
                .isInstanceOf(SurgeryRevisionConflictException.class);
    }

    @Test
    void readinessSnapshotAndDependencyRevisionsRoundTripWithoutChangingEvidence() {
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PROC-001",
                "Clinical indication", SurgeryPriority.ROUTINE, REQUESTED_AT, actor, "readiness-db-test");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> cases.save(surgeryCase, -1));
        surgeryCase.beginPreop(actor, "readiness-db-test", REQUESTED_AT.plusSeconds(1));
        tx.executeWithoutResult(ignored -> cases.save(surgeryCase, 0));
        ReadinessSnapshot snapshot = ReadinessSnapshot.evaluate(UUID.randomUUID(),
                surgeryCase.getSurgeryCaseId(), true, true, true, true, true, true, true,
                REQUESTED_AT.plusSeconds(2), Arrays.stream(SurgeryDependencyType.values())
                        .map(type -> new SurgeryDependencyRevision(type, UUID.randomUUID(), 1))
                        .toList(), REQUESTED_AT.plusSeconds(3600));
        surgeryCase.markReady(snapshot, actor, "readiness-db-test");
        tx.executeWithoutResult(ignored -> cases.save(surgeryCase, 1));

        SurgeryCase restored = tx.execute(ignored -> cases.findById(surgeryCase.getSurgeryCaseId())
                .orElseThrow());
        assertThat(restored.getStatus()).isEqualTo(SurgeryStatus.READY);
        assertThat(restored.getReadinessSnapshot()).isEqualTo(snapshot);
        assertThat(restored.getRevisionHistory()).hasSize(3);
    }

    @Test
    void twoIndependentTransactionsCannotReserveTheSameRoom() throws Exception {
        UUID roomId = UUID.randomUUID();
        Instant start = REQUESTED_AT.plusSeconds(3600);
        SurgerySchedule first = draft(roomId, UUID.randomUUID(), start, start.plusSeconds(3600));
        SurgerySchedule second = draft(roomId, UUID.randomUUID(), start.plusSeconds(600), start.plusSeconds(1800));
        CyclicBarrier startTogether = new CyclicBarrier(2);
        Callable<Boolean> reserveFirst = () -> reserveAfterBarrier(first, startTogether);
        Callable<Boolean> reserveSecond = () -> reserveAfterBarrier(second, startTogether);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> resultOne = workers.submit(reserveFirst);
            Future<Boolean> resultTwo = workers.submit(reserveSecond);
            assertThat(List.of(resultOne.get(30, TimeUnit.SECONDS), resultTwo.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_resource_reservation
                WHERE resource_id = ? AND status = 'RESERVED'
                """, Integer.class, roomId)).isEqualTo(1);
        Integer bookedCases = jdbc.queryForObject("""
                SELECT count(DISTINCT surgery_case_id) FROM surgery_resource_reservation
                WHERE surgery_case_id IN (?, ?) AND status = 'RESERVED'
                """, Integer.class, first.surgeryCaseId(), second.surgeryCaseId());
        assertThat(bookedCases).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_resource_reservation
                WHERE surgery_case_id IN (?, ?) AND status = 'RESERVED'
                """, Integer.class, first.surgeryCaseId(), second.surgeryCaseId()))
                .isEqualTo(2); // one room plus one staff, never partial booking
    }

    @Test
    void adjacentSlotIsAllowedButInUseOverrunBlocksFollowingSlot() {
        UUID roomId = UUID.randomUUID();
        Instant start = REQUESTED_AT.plusSeconds(7200);
        SurgerySchedule first = draft(roomId, UUID.randomUUID(), start, start.plusSeconds(1800));
        SurgerySchedule adjacent = draft(roomId, UUID.randomUUID(), start.plusSeconds(1800),
                start.plusSeconds(3600));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> resources.reserve(first, start));
        tx.executeWithoutResult(ignored -> resources.reserve(adjacent, start));
        jdbc.update("UPDATE surgery_case SET status = 'SCHEDULED' WHERE surgery_case_id = ?",
                first.surgeryCaseId());
        tx.executeWithoutResult(ignored -> resources.markInUse(
                first.surgeryCaseId(), first.scheduleId(), first.revision(), start));

        SurgerySchedule later = draft(roomId, UUID.randomUUID(), start.plusSeconds(3600),
                start.plusSeconds(5400));
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> resources.reserve(later, start)))
                .isInstanceOf(SurgeryScheduleConflictException.class);
    }

    @Test
    void staleReleaseCannotTouchNewScheduleRevision() {
        UUID roomId = UUID.randomUUID();
        Instant start = REQUESTED_AT.plusSeconds(10800);
        SurgerySchedule first = draft(roomId, UUID.randomUUID(), start, start.plusSeconds(1800));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> resources.reserve(first, start));
        tx.executeWithoutResult(ignored -> resources.release(first.surgeryCaseId(),
                first.scheduleId(), first.revision(), start.plusSeconds(1)));
        // The application reschedule command will perform these case/schedule transitions atomically.
        jdbc.update("UPDATE surgery_case SET status = 'PREOP_IN_PROGRESS' WHERE surgery_case_id = ?",
                first.surgeryCaseId());
        jdbc.update("UPDATE surgery_schedule SET status = 'DRAFT' WHERE schedule_id = ?", first.scheduleId());
        SurgerySchedule replacement = first.revise(roomId,
                start.plusSeconds(3600), start.plusSeconds(5400), first.teamAssignments());
        tx.executeWithoutResult(ignored -> schedules.saveDraft(replacement, 1, start.plusSeconds(2)));
        jdbc.update("UPDATE surgery_case SET status = 'READY' WHERE surgery_case_id = ?",
                first.surgeryCaseId());
        tx.executeWithoutResult(ignored -> resources.reserve(replacement, start.plusSeconds(3)));

        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> resources.release(
                first.surgeryCaseId(), first.scheduleId(), first.revision(), start.plusSeconds(4))))
                .isInstanceOf(SurgeryScheduleConflictException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_resource_reservation
                WHERE surgery_case_id = ? AND schedule_revision = 2 AND status = 'RESERVED'
                """, Integer.class, first.surgeryCaseId())).isEqualTo(2);
    }

    @Test
    void sharedStaffConflictsEvenWhenRoomsDiffer() {
        UUID staffId = UUID.randomUUID();
        Instant start = REQUESTED_AT.plusSeconds(14400);
        SurgerySchedule first = draft(UUID.randomUUID(), staffId, start, start.plusSeconds(1800));
        SurgerySchedule second = draft(UUID.randomUUID(), staffId, start.plusSeconds(300),
                start.plusSeconds(1500));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> resources.reserve(first, start));
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> resources.reserve(second, start)))
                .isInstanceOf(SurgeryScheduleConflictException.class);
    }

    private boolean reserveAfterBarrier(SurgerySchedule schedule, CyclicBarrier barrier) throws Exception {
        barrier.await(20, TimeUnit.SECONDS);
        try {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(ignored -> resources.reserve(schedule, REQUESTED_AT));
            return true;
        } catch (SurgeryScheduleConflictException expected) {
            return false;
        }
    }

    private SurgerySchedule draft(UUID roomId, UUID staffId, Instant start, Instant end) {
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), staffId);
        SurgeryCase created = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), staffId, "PROC-001",
                "Clinical indication", SurgeryPriority.ROUTINE, REQUESTED_AT, actor, "booking-test");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> cases.save(created, -1));
        created.beginPreop(actor, "booking-test", REQUESTED_AT.plusSeconds(1));
        tx.executeWithoutResult(ignored -> cases.save(created, 0));
        SurgerySchedule schedule = new SurgerySchedule(UUID.randomUUID(), created.getSurgeryCaseId(),
                1, roomId, start, end,
                List.of(new SurgeryTeamAssignment(staffId, SurgeryTeamRole.PRIMARY_SURGEON)));
        tx.executeWithoutResult(ignored -> schedules.saveDraft(schedule, 0, REQUESTED_AT.plusSeconds(2)));
        assertThat(schedules.findByCaseId(created.getSurgeryCaseId())).contains(schedule);
        // This fixture isolates the reservation engine; READY policy is covered by the domain tests.
        jdbc.update("UPDATE surgery_case SET status = 'READY' WHERE surgery_case_id = ?",
                created.getSurgeryCaseId());
        return schedule;
    }
}
