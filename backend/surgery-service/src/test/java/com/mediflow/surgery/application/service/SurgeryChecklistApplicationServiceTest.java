package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.UpdateChecklistItemUseCase;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryChecklistItem;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemDefinition;
import com.mediflow.surgery.domain.model.SurgeryChecklistSnapshot;
import com.mediflow.surgery.domain.model.SurgeryChecklistStatus;
import com.mediflow.surgery.domain.model.SurgeryChecklistTemplate;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurgeryChecklistApplicationServiceTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-29T01:00:00Z");
    private static final Instant NOW = REQUESTED_AT.plusSeconds(30);
    private static final String CORRELATION_ID = "checklist-unit-test";

    @Mock private SurgeryCaseRepositoryPort cases;
    @Mock private SurgeryChecklistRepositoryPort checklists;
    @Mock private SurgeryCommandReceiptPort receipts;
    @Mock private SurgeryScheduleRepositoryPort schedules;
    @Mock private SurgeryResourceReservationPort reservations;
    @Mock private SurgeryClockPort clock;
    @InjectMocks private SurgeryChecklistApplicationService service;

    @Test
    void updateChecklistItem_failedEvidenceCheck_appendsRevisionAndReceipt() {
        UUID caseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = newCase(caseId, actor);
        surgeryCase.beginPreop(actor, CORRELATION_ID, REQUESTED_AT.plusSeconds(1));
        SurgeryChecklistItemDefinition definition = new SurgeryChecklistItemDefinition(
                UUID.randomUUID(), "IDENTITY_CONFIRMED", true, 1);
        SurgeryChecklistSnapshot snapshot = new SurgeryChecklistTemplate(
                UUID.randomUUID(), "PROC-001", 1, java.util.List.of(definition))
                .snapshotForCase(UUID.randomUUID(), caseId);
        SurgeryChecklistItem pending = snapshot.items().getFirst();
        itemId = pending.checklistItemId();
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, receiptId, null, null));
        when(cases.lockById(caseId)).thenReturn(Optional.of(surgeryCase));
        when(checklists.findSnapshotByCaseId(caseId)).thenReturn(Optional.of(snapshot));
        when(clock.now()).thenReturn(NOW);

        var outcome = service.update(new UpdateChecklistItemUseCase.Command(
                caseId, itemId, 1, 0, 0, SurgeryChecklistStatus.FAILED,
                null, null, "check-1", actor, CORRELATION_ID));

        assertThat(outcome.state()).isEqualTo(SurgeryChecklistStatus.FAILED.name());
        assertThat(outcome.caseRevision()).isEqualTo(2);
        assertThat(outcome.subjectRevision()).isEqualTo(1);
        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getRevision()).isEqualTo(2);
        verify(cases).save(surgeryCase, 1);

        ArgumentCaptor<SurgeryChecklistSnapshot> revised = ArgumentCaptor.forClass(SurgeryChecklistSnapshot.class);
        verify(checklists).saveItemChange(revised.capture(), eq(0L), any());
        assertThat(revised.getValue().revision()).isEqualTo(1);
        assertThat(revised.getValue().items().getFirst().status()).isEqualTo(SurgeryChecklistStatus.FAILED);
        verify(receipts).complete(eq(receiptId), eq(caseId), eq("UPDATE_CHECKLIST_ITEM"), any(), eq(NOW));
        verifyNoInteractions(schedules, reservations);
    }

    @Test
    void updateChecklistItem_notApplicableWithoutPolicy_rejectsBeforeReceiptClaim() {
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        UpdateChecklistItemUseCase.Command command = new UpdateChecklistItemUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), 0, 0, 0,
                SurgeryChecklistStatus.NOT_APPLICABLE, null, null, "check-na", actor, CORRELATION_ID);

        assertThatThrownBy(() -> service.update(command))
                .hasFieldOrPropertyWithValue("code", "SURGERY_NOT_APPLICABLE_POLICY_UNCONFIRMED");
        verifyNoInteractions(cases, checklists, receipts, schedules, reservations, clock);
    }

    @Test
    void updateChecklistItem_systemActor_rejectsBeforeReceiptClaim() {
        UpdateChecklistItemUseCase.Command command = new UpdateChecklistItemUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), 0, 0, 0,
                SurgeryChecklistStatus.FAILED, null, null, "check-system",
                SurgeryAuditActor.system("clinical-service"), CORRELATION_ID);

        assertThatThrownBy(() -> service.update(command))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cases, checklists, receipts, schedules, reservations, clock);
    }

    @Test
    void updateChecklistItem_satisfiedWithoutEvidence_rejectsBeforeReceiptClaim() {
        UpdateChecklistItemUseCase.Command command = new UpdateChecklistItemUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), 0, 0, 0,
                SurgeryChecklistStatus.SATISFIED, null, null, "check-no-evidence",
                SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID()), CORRELATION_ID);

        assertThatThrownBy(() -> service.update(command))
                .hasFieldOrPropertyWithValue("code", "SURGERY_CHECKLIST_EVIDENCE_REQUIRED");
        verifyNoInteractions(cases, checklists, receipts, schedules, reservations, clock);
    }

    @Test
    void updateChecklistItem_evidenceRevisionWithoutReference_rejectsBeforeReceiptClaim() {
        UpdateChecklistItemUseCase.Command command = new UpdateChecklistItemUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), 0, 0, 0,
                SurgeryChecklistStatus.FAILED, null, 4L, "check-orphan-revision",
                SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID()), CORRELATION_ID);

        assertThatThrownBy(() -> service.update(command))
                .hasFieldOrPropertyWithValue("code", "SURGERY_CHECKLIST_EVIDENCE_REFERENCE_REQUIRED");
        verifyNoInteractions(cases, checklists, receipts, schedules, reservations, clock);
    }

    @Test
    void updateChecklistItem_staleSnapshotRevision_rejectsWithoutWritingHistoryOrReceipt() {
        UUID caseId = UUID.randomUUID();
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = newCase(caseId, actor);
        surgeryCase.beginPreop(actor, CORRELATION_ID, REQUESTED_AT.plusSeconds(1));
        SurgeryChecklistSnapshot snapshot = new SurgeryChecklistTemplate(
                UUID.randomUUID(), "PROC-001", 1,
                List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(), "IDENTITY_CONFIRMED", true, 1)))
                .snapshotForCase(UUID.randomUUID(), caseId);
        SurgeryChecklistItem item = snapshot.items().getFirst();
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, UUID.randomUUID(), null, null));
        when(cases.lockById(caseId)).thenReturn(Optional.of(surgeryCase));
        when(checklists.findSnapshotByCaseId(caseId)).thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() -> service.update(new UpdateChecklistItemUseCase.Command(
                caseId, item.checklistItemId(), 1, 1, 0, SurgeryChecklistStatus.FAILED,
                null, null, "check-stale-snapshot", actor, CORRELATION_ID)))
                .isInstanceOf(SurgeryRevisionConflictException.class);

        verify(checklists).findSnapshotByCaseId(caseId);
        verify(checklists, never()).saveItemChange(any(), anyLong(), any());
        verify(cases, never()).save(any(), anyLong());
        verify(receipts, never()).complete(any(), any(), anyString(), any(), any());
        verifyNoInteractions(clock, schedules, reservations);
    }

    @Test
    void updateChecklistItem_whenScheduled_invalidatesAndReleasesExactScheduleRevision() {
        UUID caseId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = newCase(caseId, actor);
        surgeryCase.beginPreop(actor, CORRELATION_ID, REQUESTED_AT.plusSeconds(1));
        ReadinessSnapshot readiness = ReadinessSnapshot.evaluate(UUID.randomUUID(), caseId,
                true, true, true, true, true, true, true, REQUESTED_AT.plusSeconds(2),
                Arrays.stream(SurgeryDependencyType.values())
                        .map(type -> new SurgeryDependencyRevision(type, UUID.randomUUID(), 1)).toList(),
                REQUESTED_AT.plusSeconds(3600));
        surgeryCase.markReady(readiness, actor, CORRELATION_ID);
        surgeryCase.finalizeSchedule(actor, CORRELATION_ID, REQUESTED_AT.plusSeconds(3));

        SurgeryChecklistSnapshot snapshot = new SurgeryChecklistTemplate(
                UUID.randomUUID(), "PROC-001", 1,
                List.of(new SurgeryChecklistItemDefinition(UUID.randomUUID(), "IDENTITY_CONFIRMED", true, 1)))
                .snapshotForCase(UUID.randomUUID(), caseId);
        SurgeryChecklistItem item = snapshot.items().getFirst();
        SurgerySchedule schedule = new SurgerySchedule(UUID.randomUUID(), caseId, 7, UUID.randomUUID(),
                NOW.plusSeconds(3600), NOW.plusSeconds(5400), List.of(new SurgeryTeamAssignment(
                actor.verifiedStaffId(), SurgeryTeamRole.PRIMARY_SURGEON)));
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, receiptId, null, null));
        when(cases.lockById(caseId)).thenReturn(Optional.of(surgeryCase));
        when(checklists.findSnapshotByCaseId(caseId)).thenReturn(Optional.of(snapshot));
        when(schedules.findByCaseId(caseId)).thenReturn(Optional.of(schedule));
        when(clock.now()).thenReturn(NOW);

        var outcome = service.update(new UpdateChecklistItemUseCase.Command(
                caseId, item.checklistItemId(), 3, 0, 0, SurgeryChecklistStatus.FAILED,
                null, null, "checklist-scheduled", actor, CORRELATION_ID));

        assertThat(outcome.caseRevision()).isEqualTo(surgeryCase.getRevision());
        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getReadinessSnapshot()).isNull();
        verify(reservations).release(caseId, schedule.scheduleId(), schedule.revision(), NOW);
        verify(cases).save(surgeryCase, 3);
        verify(checklists).saveItemChange(any(), eq(0L), any());
    }

    private static SurgeryCase newCase(UUID caseId, SurgeryAuditActor actor) {
        return SurgeryCase.create(caseId, UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PROC-001",
                "Clinical indication", SurgeryPriority.ROUTINE, REQUESTED_AT, actor, CORRELATION_ID);
    }
}
