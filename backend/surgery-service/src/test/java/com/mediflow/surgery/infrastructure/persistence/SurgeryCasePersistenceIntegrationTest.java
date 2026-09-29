package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.in.BeginPreopUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryResultRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.application.exception.SurgeryScheduleConflictException;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryChecklistItem;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemChange;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemDefinition;
import com.mediflow.surgery.domain.model.SurgeryChecklistStatus;
import com.mediflow.surgery.domain.model.SurgeryChecklistTemplate;
import com.mediflow.surgery.domain.model.SurgeryConsentRecord;
import com.mediflow.surgery.domain.model.SurgeryConsentSignerType;
import com.mediflow.surgery.domain.model.SurgeryConsentType;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryPerformedItem;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgeryResult;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
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
        "spring.cloud.discovery.enabled=false",
        "mediflow.features.surgery.enabled=true"
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
    @Autowired private SurgeryChecklistRepositoryPort checklists;
    @Autowired private SurgeryConsentRepositoryPort consents;
    @Autowired private SurgeryResultRepositoryPort results;
    @Autowired private SurgeryScheduleRepositoryPort schedules;
    @Autowired private SurgeryResourceReservationPort resources;
    @Autowired private BeginPreopUseCase beginPreop;
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
    void reversedTeamInputOrderStillSerializesSharedStaffAndNeverPartiallyBooks() throws Exception {
        UUID firstStaffId = new UUID(0, 101);
        UUID secondStaffId = new UUID(0, 202);
        Instant start = REQUESTED_AT.plusSeconds(5400);
        List<SurgeryTeamAssignment> firstTeam = List.of(
                new SurgeryTeamAssignment(firstStaffId, SurgeryTeamRole.PRIMARY_SURGEON),
                new SurgeryTeamAssignment(secondStaffId, SurgeryTeamRole.ANESTHESIOLOGIST));
        List<SurgeryTeamAssignment> reversedTeam = List.of(
                new SurgeryTeamAssignment(secondStaffId, SurgeryTeamRole.ANESTHESIOLOGIST),
                new SurgeryTeamAssignment(firstStaffId, SurgeryTeamRole.PRIMARY_SURGEON));
        SurgerySchedule first = draft(UUID.randomUUID(), firstTeam, start, start.plusSeconds(1800));
        SurgerySchedule second = draft(UUID.randomUUID(), reversedTeam,
                start.plusSeconds(300), start.plusSeconds(1500));
        CyclicBarrier startTogether = new CyclicBarrier(2);

        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> resultOne = workers.submit(() -> reserveAfterBarrier(first, startTogether));
            Future<Boolean> resultTwo = workers.submit(() -> reserveAfterBarrier(second, startTogether));
            assertThat(List.of(resultOne.get(30, TimeUnit.SECONDS), resultTwo.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_resource_reservation
                WHERE status = 'RESERVED' AND surgery_case_id IN (?, ?)
                """, Integer.class, first.surgeryCaseId(), second.surgeryCaseId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT surgery_case_id) FROM surgery_resource_reservation
                WHERE status = 'RESERVED' AND surgery_case_id IN (?, ?)
                """, Integer.class, first.surgeryCaseId(), second.surgeryCaseId())).isEqualTo(1);
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
    void fullyContainedRoomIntervalConflictsEvenWithDifferentStaff() {
        UUID roomId = UUID.randomUUID();
        Instant start = REQUESTED_AT.plusSeconds(9000);
        SurgerySchedule outer = draft(roomId, UUID.randomUUID(), start, start.plusSeconds(3600));
        SurgerySchedule contained = draft(roomId, UUID.randomUUID(),
                start.plusSeconds(600), start.plusSeconds(1200));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> resources.reserve(outer, start));

        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> resources.reserve(contained, start)))
                .isInstanceOf(SurgeryScheduleConflictException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_resource_reservation
                WHERE surgery_case_id = ? AND status IN ('RESERVED', 'IN_USE')
                """, Integer.class, contained.surgeryCaseId())).isZero();
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
    void releasedSchedule_canBeRevisedAfterCaseReturnsToPreop_andKeepsPriorHistory() {
        UUID staffId = UUID.randomUUID();
        UUID replacementStaffId = UUID.randomUUID();
        UUID originalRoomId = UUID.randomUUID();
        UUID replacementRoomId = UUID.randomUUID();
        Instant start = REQUESTED_AT.plusSeconds(18000);
        SurgerySchedule original = draft(originalRoomId, staffId, start, start.plusSeconds(1800));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(ignored -> resources.reserve(original, start));
        tx.executeWithoutResult(ignored -> resources.release(original.surgeryCaseId(),
                original.scheduleId(), original.revision(), start.plusSeconds(1)));
        jdbc.update("""
                UPDATE surgery_case
                SET status = 'PREOP_IN_PROGRESS', readiness_snapshot_id = NULL, ready_at = NULL
                WHERE surgery_case_id = ?
                """, original.surgeryCaseId());
        SurgerySchedule replacement = original.revise(replacementRoomId,
                start.plusSeconds(3600), start.plusSeconds(5400), List.of(
                        new SurgeryTeamAssignment(replacementStaffId, SurgeryTeamRole.PRIMARY_SURGEON)));

        tx.executeWithoutResult(ignored -> schedules.saveDraft(replacement, 1, start.plusSeconds(2)));

        assertThat(schedules.findByCaseId(original.surgeryCaseId())).contains(replacement);
        assertThat(schedules.findRevision(original.surgeryCaseId(), 1)).contains(original);
        assertThat(schedules.findRevision(original.surgeryCaseId(), 2)).contains(replacement);
        assertThat(schedules.findRevision(original.surgeryCaseId(), 1).orElseThrow().teamAssignments())
                .containsExactlyElementsOf(original.teamAssignments());
        assertThat(schedules.findRevision(original.surgeryCaseId(), 2).orElseThrow().teamAssignments())
                .containsExactlyElementsOf(replacement.teamAssignments());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_resource_reservation
                WHERE surgery_case_id = ? AND schedule_revision = 1 AND status = 'RELEASED'
                """, Integer.class, original.surgeryCaseId())).isEqualTo(2);
    }

    @Test
    void checklistTemplateSnapshotAndItemHistory_roundTripWithOptimisticRevision() {
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = persistRequestedCase(actor, "checklist-persistence-test");
        Instant recordedAt = REQUESTED_AT.plusSeconds(120);
        SurgeryChecklistTemplate template = new SurgeryChecklistTemplate(UUID.randomUUID(), "PROC-CHK-01", 1,
                List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(), "IDENTITY_CONFIRMED", true, 1),
                        new SurgeryChecklistItemDefinition(UUID.randomUUID(), "ALLERGY_REVIEWED", true, 2)));
        var snapshot = template.snapshotForCase(UUID.randomUUID(), surgeryCase.getSurgeryCaseId());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> {
            checklists.createTemplate(template, recordedAt);
            checklists.createSnapshot(snapshot);
        });

        assertThat(checklists.findTemplate("PROC-CHK-01", 1)).contains(template);
        assertThat(checklists.findSnapshotByCaseId(surgeryCase.getSurgeryCaseId())).contains(snapshot);

        SurgeryChecklistItem pending = snapshot.items().getFirst();
        UUID evidenceId = UUID.randomUUID();
        SurgeryChecklistItem changed = pending.revise(SurgeryChecklistStatus.FAILED, evidenceId, 3L);
        var revisedSnapshot = snapshot.reviseItem(pending.checklistItemId(), 0, changed);
        SurgeryChecklistItemChange change = new SurgeryChecklistItemChange(UUID.randomUUID(),
                pending.checklistItemId(), changed.revision(), pending.status(), changed.status(),
                evidenceId, 3L, actor, recordedAt.plusSeconds(1), "checklist-pg-1");
        tx.executeWithoutResult(ignored -> checklists.saveItemChange(revisedSnapshot, 0, change));

        assertThat(checklists.findSnapshotByCaseId(surgeryCase.getSurgeryCaseId()))
                .contains(revisedSnapshot);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM preop_checklist_item_history WHERE checklist_item_id = ?
                """, Integer.class, pending.checklistItemId())).isEqualTo(1);
        assertThatThrownBy(() -> tx.executeWithoutResult(
                ignored -> checklists.saveItemChange(revisedSnapshot, 0, change)))
                .isInstanceOf(SurgeryRevisionConflictException.class);
        assertThat(jdbc.queryForObject("""
                SELECT revision FROM preop_checklist_snapshot WHERE surgery_case_id = ?
                """, Long.class, surgeryCase.getSurgeryCaseId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM preop_checklist_item_history WHERE checklist_item_id = ?
                """, Integer.class, pending.checklistItemId())).isEqualTo(1);
    }

    @Test
    void consentPersistence_signsRevokesAndReconsentsWithoutReplacingAudit() {
        SurgeryAuditActor signerRecorder = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryAuditActor revoker = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = persistRequestedCase(signerRecorder, "consent-persistence-test");
        UUID consentId = UUID.randomUUID();
        Instant signedAt = REQUESTED_AT.plusSeconds(240);
        SurgeryConsentRecord signed = SurgeryConsentRecord.sign(consentId, surgeryCase.getSurgeryCaseId(),
                SurgeryConsentType.SURGERY, UUID.randomUUID(), SurgeryConsentSignerType.PATIENT,
                UUID.randomUUID(), signerRecorder, signedAt, "consent-sign-pg");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> assertThat(consents.save(signed)).isEqualTo(signed));
        tx.executeWithoutResult(ignored -> assertThat(consents.save(signed)).isEqualTo(signed));

        SurgeryConsentRecord conflictingActive = SurgeryConsentRecord.sign(UUID.randomUUID(),
                surgeryCase.getSurgeryCaseId(), SurgeryConsentType.SURGERY, UUID.randomUUID(),
                SurgeryConsentSignerType.PATIENT, null, signerRecorder, signedAt.plusSeconds(1),
                "consent-conflict-pg");
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> consents.save(conflictingActive)))
                .isInstanceOf(DataIntegrityViolationException.class);

        SurgeryConsentRecord revoked = signed.revoke(revoker, signedAt.plusSeconds(60),
                "consent-revoke-pg", "Signer withdrew permission");
        tx.executeWithoutResult(ignored -> assertThat(consents.save(revoked)).isEqualTo(revoked));
        assertThat(consents.findById(consentId)).contains(revoked);
        assertThat(consents.findByCaseId(surgeryCase.getSurgeryCaseId())).containsExactly(revoked);
        assertThat(jdbc.queryForList("""
                SELECT action FROM surgery_consent_history WHERE consent_id = ? ORDER BY sequence_no
                """, String.class, consentId)).containsExactly("SIGNED", "REVOKED");

        SurgeryConsentRecord replacement = SurgeryConsentRecord.sign(UUID.randomUUID(),
                surgeryCase.getSurgeryCaseId(), SurgeryConsentType.SURGERY, UUID.randomUUID(),
                SurgeryConsentSignerType.PATIENT, null, signerRecorder, signedAt.plusSeconds(120),
                "consent-resign-pg");
        tx.executeWithoutResult(ignored -> consents.save(replacement));
        assertThat(consents.findByCaseId(surgeryCase.getSurgeryCaseId()))
                .containsExactlyInAnyOrder(revoked, replacement);
    }

    @Test
    void resultPersistence_roundTripsPerformedItemsAndRejectsReplacement() {
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = persistInProgressCase(actor, "result-persistence-test");
        Instant startedAt = REQUESTED_AT.plusSeconds(300);
        SurgeryResult result = new SurgeryResult(UUID.randomUUID(), surgeryCase.getSurgeryCaseId(),
                "PROC-RESULT-01", "METHOD-01", "SUCCESS", null,
                startedAt, startedAt.plusSeconds(600), List.of(
                        new SurgeryPerformedItem(new UUID(0, 1), "ITEM-01", "PRICE-01", new BigDecimal("2.00")),
                        new SurgeryPerformedItem(new UUID(0, 2), "ITEM-02", "PRICE-02", new BigDecimal("1.5"))),
                startedAt.plusSeconds(601), actor, "result-pg-1");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(ignored -> assertThat(results.create(result)).isEqualTo(result));
        tx.executeWithoutResult(ignored -> assertThat(results.create(result)).isEqualTo(result));

        assertThat(results.findByCaseId(surgeryCase.getSurgeryCaseId())).contains(result);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_performed_item WHERE result_id = ?
                """, Integer.class, result.resultId())).isEqualTo(2);
        SurgeryResult changed = new SurgeryResult(UUID.randomUUID(), surgeryCase.getSurgeryCaseId(),
                result.procedureCode(), "METHOD-02", result.treatmentOutcomeCode(), null,
                result.actualStartAt(), result.actualEndAt(), result.performedItems(),
                result.recordedAt(), actor, "result-pg-2");
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> results.create(changed)))
                .isInstanceOf(SurgeryRevisionConflictException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_result WHERE surgery_case_id = ?
                """, Integer.class, surgeryCase.getSurgeryCaseId())).isEqualTo(1);
    }

    @Test
    void childPersistence_rollsBackChecklistConsentResultAndAuditRowsTogether() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase requestedCase = persistRequestedCase(actor, "child-rollback-test");
        String procedureCode = "PROC-ROLLBACK-" + UUID.randomUUID();
        SurgeryChecklistTemplate template = new SurgeryChecklistTemplate(UUID.randomUUID(), procedureCode, 1,
                List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(), "IDENTITY_CONFIRMED", true, 1)));
        var snapshot = template.snapshotForCase(UUID.randomUUID(), requestedCase.getSurgeryCaseId());

        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> {
            checklists.createTemplate(template, REQUESTED_AT);
            checklists.createSnapshot(snapshot);
            throw new IllegalStateException("simulate failure after checklist child writes");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(checklists.findTemplate(procedureCode, 1)).isEmpty();
        assertThat(checklists.findSnapshotByCaseId(requestedCase.getSurgeryCaseId())).isEmpty();

        UUID consentId = UUID.randomUUID();
        SurgeryConsentRecord consent = SurgeryConsentRecord.sign(consentId,
                requestedCase.getSurgeryCaseId(), SurgeryConsentType.ANESTHESIA, UUID.randomUUID(),
                SurgeryConsentSignerType.PATIENT, null, actor, REQUESTED_AT, "consent-rollback");
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> {
            consents.save(consent);
            throw new IllegalStateException("simulate failure after consent audit write");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(consents.findById(consentId)).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_consent_history WHERE consent_id = ?
                """, Integer.class, consentId)).isZero();

        SurgeryCase inProgressCase = persistInProgressCase(actor, "result-rollback-test");
        UUID resultId = UUID.randomUUID();
        SurgeryResult result = new SurgeryResult(resultId, inProgressCase.getSurgeryCaseId(),
                "PROC-RESULT-ROLLBACK", "METHOD-01", "SUCCESS", null,
                REQUESTED_AT.plusSeconds(400), REQUESTED_AT.plusSeconds(500), List.of(
                        new SurgeryPerformedItem(UUID.randomUUID(), "ITEM-ROLLBACK", "PRICE-01",
                                new BigDecimal("1.00"))),
                REQUESTED_AT.plusSeconds(501), actor, "result-rollback");
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> {
            results.create(result);
            throw new IllegalStateException("simulate failure after result item writes");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(results.findByCaseId(inProgressCase.getSurgeryCaseId())).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_performed_item WHERE result_id = ?
                """, Integer.class, resultId)).isZero();
    }

    @Test
    void beginPreopPersistence_commitsCaseAndReceiptTogetherAndReplaysAfterCommit() {
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = persistRequestedCase(actor, "begin-preop-pg");
        BeginPreopUseCase.Command command = new BeginPreopUseCase.Command(
                surgeryCase.getSurgeryCaseId(), 0, "begin-preop-pg-key", actor, "begin-preop-pg-correlation");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> {
            assertThat(beginPreop.begin(command).replayed()).isFalse();
            throw new IllegalStateException("simulate failure before outer commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(cases.findById(surgeryCase.getSurgeryCaseId()).orElseThrow().getStatus())
                .isEqualTo(SurgeryStatus.REQUESTED);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_command_receipt
                WHERE idempotency_key = ?
                """, Integer.class, "begin-preop-pg-key")).isZero();

        var applied = beginPreop.begin(command);
        var replay = beginPreop.begin(command);
        assertThat(applied.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.caseRevision()).isEqualTo(1);
        assertThat(cases.findById(surgeryCase.getSurgeryCaseId()).orElseThrow().getStatus())
                .isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_status_history WHERE surgery_case_id = ?
                """, Integer.class, surgeryCase.getSurgeryCaseId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM surgery_command_receipt
                WHERE idempotency_key = ? AND response_code = 'BEGIN_PREOP' AND status = 'APPLIED'
                """, Integer.class, "begin-preop-pg-key")).isEqualTo(1);
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
        return draft(roomId, List.of(new SurgeryTeamAssignment(staffId, SurgeryTeamRole.PRIMARY_SURGEON)),
                start, end);
    }

    private SurgerySchedule draft(UUID roomId, List<SurgeryTeamAssignment> teamAssignments,
                                  Instant start, Instant end) {
        UUID staffId = teamAssignments.getFirst().staffId();
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
                1, roomId, start, end, teamAssignments);
        tx.executeWithoutResult(ignored -> schedules.saveDraft(schedule, 0, REQUESTED_AT.plusSeconds(2)));
        assertThat(schedules.findByCaseId(created.getSurgeryCaseId())).contains(schedule);
        // This fixture isolates the reservation engine; READY policy is covered by the domain tests.
        jdbc.update("UPDATE surgery_case SET status = 'READY' WHERE surgery_case_id = ?",
                created.getSurgeryCaseId());
        return schedule;
    }

    private SurgeryCase persistRequestedCase(SurgeryAuditActor actor, String correlationId) {
        SurgeryCase surgeryCase = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), actor.verifiedStaffId(), "PROC-001",
                "Clinical indication", SurgeryPriority.ROUTINE, REQUESTED_AT, actor, correlationId);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> cases.save(surgeryCase, -1));
        return surgeryCase;
    }

    private SurgeryCase persistInProgressCase(SurgeryAuditActor actor, String correlationId) {
        SurgeryCase surgeryCase = persistRequestedCase(actor, correlationId);
        Instant preopAt = REQUESTED_AT.plusSeconds(301);
        surgeryCase.beginPreop(actor, correlationId, preopAt);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> cases.save(surgeryCase, 0));

        Instant evaluatedAt = REQUESTED_AT.plusSeconds(302);
        ReadinessSnapshot snapshot = ReadinessSnapshot.evaluate(UUID.randomUUID(),
                surgeryCase.getSurgeryCaseId(), true, true, true, true, true, true, true,
                evaluatedAt, Arrays.stream(SurgeryDependencyType.values())
                        .map(type -> new SurgeryDependencyRevision(type, UUID.randomUUID(), 1)).toList(),
                evaluatedAt.plusSeconds(3600));
        surgeryCase.markReady(snapshot, actor, correlationId);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> cases.save(surgeryCase, 1));
        surgeryCase.finalizeSchedule(actor, correlationId, evaluatedAt.plusSeconds(1));
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> cases.save(surgeryCase, 2));
        surgeryCase.start(snapshot, actor, correlationId, evaluatedAt.plusSeconds(2));
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> cases.save(surgeryCase, 3));
        return surgeryCase;
    }
}
