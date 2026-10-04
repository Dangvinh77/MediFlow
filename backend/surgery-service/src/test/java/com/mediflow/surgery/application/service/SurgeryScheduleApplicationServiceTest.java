package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.port.in.PrepareSurgeryScheduleUseCase;
import com.mediflow.surgery.application.port.out.OrganizationLookupPort;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurgeryScheduleApplicationServiceTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-29T01:00:00Z");
    private static final Instant NOW = REQUESTED_AT.plusSeconds(30);
    private static final String CORRELATION_ID = "schedule-draft-test";

    @Mock private SurgeryCaseRepositoryPort cases;
    @Mock private SurgeryScheduleRepositoryPort schedules;
    @Mock private SurgeryResourceReservationPort reservations;
    @Mock private SurgeryCommandReceiptPort receipts;
    @Mock private OrganizationLookupPort organization;
    @Mock private SurgeryClockPort clock;
    @InjectMocks private SurgeryScheduleApplicationService service;

    private SurgeryAuditActor actor;
    private SurgeryCase surgeryCase;
    private UUID roomId;
    private UUID surgeonId;
    private UUID nurseId;
    private UUID receiptId;

    @BeforeEach
    void setUp() {
        actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        surgeryCase = newPreopCase(actor);
        roomId = UUID.randomUUID();
        surgeonId = new UUID(0, 1);
        nurseId = new UUID(0, 2);
        receiptId = UUID.randomUUID();
    }

    private void stubNewReceipt() {
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, receiptId, null, null));
        when(cases.findById(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.of(surgeryCase));
    }

    private void stubActiveLookups() {
        when(organization.findDepartment(surgeryCase.getDepartmentId(), CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.DEPARTMENT, surgeryCase.getDepartmentId()));
        when(organization.findRoom(roomId, CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.ROOM, roomId));
        when(organization.findStaff(surgeonId, CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.STAFF, surgeonId));
        when(organization.findStaff(nurseId, CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.STAFF, nurseId));
    }

    private void stubCaseLock() {
        when(cases.lockById(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.of(surgeryCase));
    }

    private void stubDraftSave() {
        when(schedules.findByCaseId(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.empty());
        when(clock.now()).thenReturn(NOW);
    }

    @Test
    void prepare_activeReferencesAndPreopCase_persistsDraftWithoutReservingResources() {
        stubNewReceipt();
        stubActiveLookups();
        stubCaseLock();
        stubDraftSave();
        var outcome = service.prepare(command(surgeryCase, actor, 1, 0));

        assertThat(outcome.commandCode()).isEqualTo("PREPARE_SCHEDULE");
        assertThat(outcome.caseRevision()).isEqualTo(2);
        assertThat(outcome.subjectRevision()).isEqualTo(1);
        assertThat(outcome.state()).isEqualTo("DRAFT");
        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getRevision()).isEqualTo(2);
        ArgumentCaptor<SurgerySchedule> schedule = ArgumentCaptor.forClass(SurgerySchedule.class);
        verify(schedules).saveDraft(schedule.capture(), eq(0L), eq(NOW));
        assertThat(schedule.getValue().surgeryCaseId()).isEqualTo(surgeryCase.getSurgeryCaseId());
        assertThat(schedule.getValue().revision()).isEqualTo(1);
        assertThat(schedule.getValue().roomId()).isEqualTo(roomId);
        assertThat(schedule.getValue().teamAssignments()).containsExactlyInAnyOrder(
                new SurgeryTeamAssignment(surgeonId, SurgeryTeamRole.PRIMARY_SURGEON),
                new SurgeryTeamAssignment(nurseId, SurgeryTeamRole.OR_NURSE));
        verify(cases).save(surgeryCase, 1);
        verify(receipts).complete(eq(receiptId), eq(surgeryCase.getSurgeryCaseId()),
                eq("PREPARE_SCHEDULE"), any(), eq(NOW));
        verifyNoInteractions(reservations);
    }

    @Test
    void prepare_inactiveStaff_rejectsBeforeCaseLockOrScheduleMutation() {
        stubNewReceipt();
        when(organization.findDepartment(surgeryCase.getDepartmentId(), CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.DEPARTMENT, surgeryCase.getDepartmentId()));
        when(organization.findRoom(roomId, CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.ROOM, roomId));
        when(organization.findStaff(surgeonId, CORRELATION_ID)).thenReturn(inactive(
                OrganizationLookupPort.ReferenceKind.STAFF, surgeonId));

        assertThatThrownBy(() -> service.prepare(command(surgeryCase, actor, 1, 0)))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_STAFF_INELIGIBLE");

        verify(cases, never()).lockById(any());
        verify(schedules, never()).saveDraft(any(), anyLong(), any());
        verify(cases, never()).save(any(), anyLong());
        verifyNoInteractions(reservations, clock);
    }

    @Test
    void prepare_unknownRoomState_failsClosedAsUpstreamUnavailable() {
        stubNewReceipt();
        when(organization.findDepartment(surgeryCase.getDepartmentId(), CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.DEPARTMENT, surgeryCase.getDepartmentId()));
        when(organization.findRoom(roomId, CORRELATION_ID)).thenReturn(unknown(
                OrganizationLookupPort.ReferenceKind.ROOM, roomId));

        assertThatThrownBy(() -> service.prepare(command(surgeryCase, actor, 1, 0)))
                .isInstanceOf(UpstreamUnavailableException.class);

        verify(cases, never()).lockById(any());
        verify(schedules, never()).saveDraft(any(), anyLong(), any());
        verifyNoInteractions(reservations, clock);
    }

    @Test
    void prepare_lookupEchoMismatch_failsClosedBeforeLockingCase() {
        stubNewReceipt();
        when(organization.findDepartment(surgeryCase.getDepartmentId(), CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.DEPARTMENT, surgeryCase.getDepartmentId()));
        when(organization.findRoom(roomId, CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.ROOM, UUID.randomUUID()));

        assertThatThrownBy(() -> service.prepare(command(surgeryCase, actor, 1, 0)))
                .isInstanceOf(UpstreamUnavailableException.class);

        verify(cases, never()).lockById(any());
        verify(schedules, never()).saveDraft(any(), anyLong(), any());
        verifyNoInteractions(reservations, clock);
    }

    @Test
    void prepare_staleCaseRevision_rejectsWithoutWritingDraftOrReceipt() {
        stubNewReceipt();
        assertThatThrownBy(() -> service.prepare(command(surgeryCase, actor, 0, 0)))
                .isInstanceOf(SurgeryRevisionConflictException.class);

        verify(cases, never()).lockById(any());
        verify(schedules, never()).findByCaseId(any());
        verify(schedules, never()).saveDraft(any(), anyLong(), any());
        verify(cases, never()).save(any(), anyLong());
        verify(receipts, never()).complete(any(), any(), anyString(), any(), any());
        verifyNoInteractions(organization);
        verifyNoInteractions(reservations);
    }

    @Test
    void prepare_staleScheduleRevision_rejectsWithoutChangingCaseOrDraft() {
        stubNewReceipt();
        stubActiveLookups();
        stubCaseLock();
        SurgerySchedule latest = new SurgerySchedule(UUID.randomUUID(), surgeryCase.getSurgeryCaseId(),
                2, roomId, NOW.plusSeconds(3600), NOW.plusSeconds(5400), List.of(
                new SurgeryTeamAssignment(surgeonId, SurgeryTeamRole.PRIMARY_SURGEON)));
        when(schedules.findByCaseId(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.prepare(command(surgeryCase, actor, 1, 0)))
                .isInstanceOf(SurgeryRevisionConflictException.class);

        verify(schedules, never()).saveDraft(any(), anyLong(), any());
        verify(cases, never()).save(any(), anyLong());
        verify(receipts, never()).complete(any(), any(), anyString(), any(), any());
        verifyNoInteractions(reservations);
    }

    @Test
    void prepare_revisingDraft_keepsScheduleIdentityAndIncrementsRevision() {
        stubNewReceipt();
        stubActiveLookups();
        stubCaseLock();
        when(clock.now()).thenReturn(NOW);
        UUID scheduleId = UUID.randomUUID();
        SurgerySchedule previous = new SurgerySchedule(scheduleId, surgeryCase.getSurgeryCaseId(),
                1, UUID.randomUUID(), NOW.plusSeconds(3600), NOW.plusSeconds(5400), List.of(
                new SurgeryTeamAssignment(surgeonId, SurgeryTeamRole.PRIMARY_SURGEON)));
        when(schedules.findByCaseId(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.of(previous));

        service.prepare(command(surgeryCase, actor, 1, 1));

        ArgumentCaptor<SurgerySchedule> schedule = ArgumentCaptor.forClass(SurgerySchedule.class);
        verify(schedules).saveDraft(schedule.capture(), eq(1L), eq(NOW));
        assertThat(schedule.getValue().scheduleId()).isEqualTo(scheduleId);
        assertThat(schedule.getValue().revision()).isEqualTo(2);
    }

    @Test
    void prepare_activeDepartmentAndResourceLookups_happenBeforeAggregateLock() {
        stubNewReceipt();
        stubActiveLookups();
        stubCaseLock();
        stubDraftSave();
        service.prepare(command(surgeryCase, actor, 1, 0));

        InOrder order = inOrder(organization, cases);
        order.verify(organization).findDepartment(surgeryCase.getDepartmentId(), CORRELATION_ID);
        order.verify(organization).findRoom(roomId, CORRELATION_ID);
        order.verify(organization, times(2)).findStaff(any(), eq(CORRELATION_ID));
        order.verify(cases).lockById(surgeryCase.getSurgeryCaseId());
    }

    @Test
    void prepare_sameKeyReplay_returnsStoredOutcomeBeforeLookingUpReferences() {
        SurgeryCommandOutcome original = new SurgeryCommandOutcome("PREPARE_SCHEDULE",
                surgeryCase.getSurgeryCaseId(), 2, UUID.randomUUID(), 1, "DRAFT", NOW, false);
        SurgeryCommandReceipts.complete(receipts, receiptId, original);
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(receipts).complete(eq(receiptId), eq(surgeryCase.getSurgeryCaseId()),
                eq("PREPARE_SCHEDULE"), payload.capture(), eq(NOW));
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.REPLAY, receiptId, "PREPARE_SCHEDULE", payload.getValue()));

        SurgeryCommandOutcome replay = service.prepare(command(surgeryCase, actor, 1, 0));

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.subjectId()).isEqualTo(original.subjectId());
        assertThat(replay.occurredAt()).isEqualTo(NOW);
        verifyNoInteractions(organization, schedules, reservations, clock);
        verify(cases, never()).findById(any());
        verify(cases, never()).lockById(any());
    }

    @Test
    void prepare_receiptPayloadConflict_rejectsBeforeOrganizationLookup() {
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.CONFLICT, receiptId, null, null));

        assertThatThrownBy(() -> service.prepare(command(surgeryCase, actor, 1, 0)))
                .isInstanceOf(SurgeryRevisionConflictException.class);

        verifyNoInteractions(organization, schedules, reservations, clock);
        verify(cases, never()).findById(any());
        verify(cases, never()).lockById(any());
    }

    private PrepareSurgeryScheduleUseCase.Command command(SurgeryCase target, SurgeryAuditActor commandActor,
                                                          long caseRevision, long scheduleRevision) {
        return new PrepareSurgeryScheduleUseCase.Command(target.getSurgeryCaseId(), caseRevision,
                scheduleRevision, roomId, NOW.plusSeconds(3600), NOW.plusSeconds(5400), List.of(
                new PrepareSurgeryScheduleUseCase.TeamMember(surgeonId, SurgeryTeamRole.PRIMARY_SURGEON),
                new PrepareSurgeryScheduleUseCase.TeamMember(nurseId, SurgeryTeamRole.OR_NURSE)),
                "prepare-" + scheduleRevision, commandActor, CORRELATION_ID);
    }

    private static OrganizationLookupPort.OrganizationLookupSnapshot active(
            OrganizationLookupPort.ReferenceKind kind, UUID id) {
        return new OrganizationLookupPort.OrganizationLookupSnapshot(kind, id,
                OrganizationLookupPort.ReferenceState.ACTIVE, NOW, "org-rev-1",
                kind == OrganizationLookupPort.ReferenceKind.STAFF ? "UNMAPPED_JOB_TITLE" : null);
    }

    private static OrganizationLookupPort.OrganizationLookupSnapshot inactive(
            OrganizationLookupPort.ReferenceKind kind, UUID id) {
        return new OrganizationLookupPort.OrganizationLookupSnapshot(kind, id,
                OrganizationLookupPort.ReferenceState.INACTIVE, NOW, "org-rev-2", null);
    }

    private static OrganizationLookupPort.OrganizationLookupSnapshot unknown(
            OrganizationLookupPort.ReferenceKind kind, UUID id) {
        return new OrganizationLookupPort.OrganizationLookupSnapshot(kind, id,
                OrganizationLookupPort.ReferenceState.UNKNOWN, NOW, null, null);
    }

    private static SurgeryCase newPreopCase(SurgeryAuditActor actor) {
        SurgeryCase surgeryCase = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), actor.verifiedStaffId(), "PROC-001",
                "Clinical indication", SurgeryPriority.ROUTINE, REQUESTED_AT, actor, CORRELATION_ID);
        surgeryCase.beginPreop(actor, CORRELATION_ID, REQUESTED_AT.plusSeconds(1));
        return surgeryCase;
    }
}
