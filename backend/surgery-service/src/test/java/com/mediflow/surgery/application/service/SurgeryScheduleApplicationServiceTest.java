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
    @Mock private com.mediflow.surgery.application.port.out.AdmissionLookupPort admissions;
    @Mock private SurgeryClockPort clock;
    @Mock private com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort events;
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
        when(organization.findSurgicalEligibility(eq(surgeonId), eq(SurgeryTeamRole.PRIMARY_SURGEON),
                any(), any(), eq(CORRELATION_ID))).thenReturn(eligibility(surgeonId,
                SurgeryTeamRole.PRIMARY_SURGEON, OrganizationLookupPort.ReferenceState.ACTIVE));
        when(organization.findSurgicalEligibility(eq(nurseId), eq(SurgeryTeamRole.OR_NURSE),
                any(), any(), eq(CORRELATION_ID))).thenReturn(eligibility(nurseId,
                SurgeryTeamRole.OR_NURSE, OrganizationLookupPort.ReferenceState.ACTIVE));
    }

    private void stubCaseLock() {
        when(cases.lockById(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.of(surgeryCase));
    }

    private void stubDraftSave() {
        when(schedules.findByCaseId(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.empty());
        when(clock.now()).thenReturn(NOW);
    }

    @Test
    void prepare_scheduledCase_invalidatesAndReleasesOnlyOldRevisionBeforeNewDraft() {
        SurgerySchedule previous = new SurgerySchedule(UUID.randomUUID(),surgeryCase.getSurgeryCaseId(),1,
                UUID.randomUUID(),NOW.plusSeconds(3600),NOW.plusSeconds(5400),List.of(
                new SurgeryTeamAssignment(surgeonId,SurgeryTeamRole.PRIMARY_SURGEON)));
        var snapshot = com.mediflow.surgery.domain.model.ReadinessSnapshot.evaluate(UUID.randomUUID(),
                surgeryCase.getSurgeryCaseId(),true,true,true,true,true,true,true,NOW.minusSeconds(10),
                java.util.Arrays.stream(com.mediflow.surgery.domain.model.SurgeryDependencyType.values())
                        .map(type -> new com.mediflow.surgery.domain.model.SurgeryDependencyRevision(type,
                                type == com.mediflow.surgery.domain.model.SurgeryDependencyType.SCHEDULE
                                        ? previous.scheduleId() : UUID.randomUUID(),1)).toList(),NOW.plusSeconds(60));
        surgeryCase.markReady(snapshot,actor,CORRELATION_ID);
        surgeryCase.finalizeSchedule(actor,CORRELATION_ID,NOW.minusSeconds(5));
        stubNewReceipt(); stubActiveLookups(); stubCaseLock();
        when(schedules.findByCaseId(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.of(previous));
        when(clock.now()).thenReturn(NOW);
        var outcome = service.prepare(command(surgeryCase,actor,3,1));
        assertThat(outcome.caseRevision()).isEqualTo(5);
        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getReadinessSnapshot()).isNull();
        var order = inOrder(reservations,schedules,cases);
        order.verify(reservations).release(surgeryCase.getSurgeryCaseId(),previous.scheduleId(),1,NOW);
        order.verify(cases).save(surgeryCase,3);
        order.verify(schedules).saveDraft(any(),eq(1L),eq(NOW));
        order.verify(cases).save(surgeryCase,4);
        verify(reservations,never()).reserve(any(),any());
    }

    @Test
    void prepare_authorityExpiresWhileWaitingForCaseLock_doesNotMutate() {
        stubNewReceipt(); stubActiveLookups(); stubCaseLock();
        when(schedules.findByCaseId(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.empty());
        when(clock.now()).thenReturn(NOW.plusSeconds(31));
        assertThatThrownBy(() -> service.prepare(command(surgeryCase,actor,1,0)))
                .isInstanceOf(UpstreamUnavailableException.class);
        verify(schedules,never()).saveDraft(any(),anyLong(),any());
        verify(cases,never()).save(any(),anyLong());
        verifyNoInteractions(reservations);
    }

    @Test
    void prepare_unknownReadinessScheduleCannotReleaseAnotherRevision() {
        SurgerySchedule previous = new SurgerySchedule(UUID.randomUUID(),surgeryCase.getSurgeryCaseId(),2,
                UUID.randomUUID(),NOW.plusSeconds(3600),NOW.plusSeconds(5400),List.of(
                new SurgeryTeamAssignment(surgeonId,SurgeryTeamRole.PRIMARY_SURGEON)));
        var snapshot = com.mediflow.surgery.domain.model.ReadinessSnapshot.evaluate(UUID.randomUUID(),
                surgeryCase.getSurgeryCaseId(),true,true,true,true,true,true,true,NOW.minusSeconds(10),
                java.util.Arrays.stream(com.mediflow.surgery.domain.model.SurgeryDependencyType.values())
                        .map(type -> new com.mediflow.surgery.domain.model.SurgeryDependencyRevision(type,
                                type == com.mediflow.surgery.domain.model.SurgeryDependencyType.SCHEDULE
                                        ? previous.scheduleId() : UUID.randomUUID(),1)).toList(),NOW.plusSeconds(60));
        surgeryCase.markReady(snapshot,actor,CORRELATION_ID);
        stubNewReceipt(); stubActiveLookups(); stubCaseLock();
        when(schedules.findByCaseId(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.of(previous));
        when(clock.now()).thenReturn(NOW);
        assertThatThrownBy(() -> service.prepare(command(surgeryCase,actor,2,2)))
                .isInstanceOf(SurgeryRevisionConflictException.class);
        verifyNoInteractions(reservations);
        verify(cases,never()).save(any(),anyLong());
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
        when(organization.findSurgicalEligibility(eq(surgeonId), eq(SurgeryTeamRole.PRIMARY_SURGEON),
                any(), any(), eq(CORRELATION_ID))).thenReturn(eligibility(surgeonId,
                SurgeryTeamRole.PRIMARY_SURGEON, OrganizationLookupPort.ReferenceState.INACTIVE));

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
    void prepare_roomInAnotherDepartment_rejectsBeforeLockOrMutation() {
        rejectRoomDepartment(UUID.randomUUID(), "SURGERY_ROOM_DEPARTMENT_MISMATCH");
    }

    @Test
    void prepare_roomDepartmentMissing_failsClosedBeforeLockOrMutation() {
        rejectRoomDepartment(null, null);
    }

    private void rejectRoomDepartment(UUID departmentId, String rule) {
        stubNewReceipt();
        when(organization.findDepartment(surgeryCase.getDepartmentId(), CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.DEPARTMENT, surgeryCase.getDepartmentId()));
        when(organization.findRoom(roomId, CORRELATION_ID)).thenReturn(
                new OrganizationLookupPort.OrganizationLookupSnapshot(OrganizationLookupPort.ReferenceKind.ROOM,
                        roomId, OrganizationLookupPort.ReferenceState.ACTIVE, NOW, "1", null, null, departmentId));
        var rejection = assertThatThrownBy(() -> service.prepare(command(surgeryCase, actor, 1, 0)));
        if (rule == null) rejection.isInstanceOf(UpstreamUnavailableException.class);
        else rejection.isInstanceOf(SurgeryRuleException.class).hasFieldOrPropertyWithValue("code", rule);
        verify(cases, never()).lockById(any());
        verify(cases, never()).save(any(), anyLong());
        verifyNoInteractions(schedules, reservations, clock);
    }

    @Test
    void prepare_staffInAnotherDepartment_rejectsBeforeLockOrMutation() {
        rejectStaffDepartment(UUID.randomUUID(), "SURGERY_STAFF_DEPARTMENT_MISMATCH");
    }

    @Test
    void prepare_staffDepartmentMissing_failsClosedBeforeLockOrMutation() {
        rejectStaffDepartment(null, null);
    }

    private void rejectStaffDepartment(UUID departmentId, String rule) {
        stubNewReceipt();
        when(organization.findDepartment(surgeryCase.getDepartmentId(), CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.DEPARTMENT, surgeryCase.getDepartmentId()));
        when(organization.findRoom(roomId, CORRELATION_ID)).thenReturn(active(
                OrganizationLookupPort.ReferenceKind.ROOM, roomId));
        when(organization.findSurgicalEligibility(eq(surgeonId), eq(SurgeryTeamRole.PRIMARY_SURGEON),
                any(), any(), eq(CORRELATION_ID))).thenReturn(new OrganizationLookupPort.SurgicalEligibilitySnapshot(
                surgeonId, SurgeryTeamRole.PRIMARY_SURGEON, OrganizationLookupPort.ReferenceState.ACTIVE,
                departmentId, NOW, "1", NOW.plusSeconds(3600), NOW.plusSeconds(5400)));
        var rejection = assertThatThrownBy(() -> service.prepare(command(surgeryCase, actor, 1, 0)));
        if (rule == null) rejection.isInstanceOf(UpstreamUnavailableException.class);
        else rejection.isInstanceOf(SurgeryRuleException.class).hasFieldOrPropertyWithValue("code", rule);
        verify(cases, never()).lockById(any());
        verify(cases, never()).save(any(), anyLong());
        verifyNoInteractions(schedules, reservations, clock);
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
        order.verify(organization, times(2)).findSurgicalEligibility(any(), any(), any(), any(), eq(CORRELATION_ID));
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
    void prepare_admissionWrongPatientOrDepartment_rejectsBeforeOrganizationOrCaseLock() {
        UUID admissionId=UUID.randomUUID();
        surgeryCase=SurgeryCase.create(UUID.randomUUID(),UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.ADMISSION,admissionId,admissionId,null),
                UUID.randomUUID(),UUID.randomUUID(),actor.verifiedStaffId(),"PROC-001","Indication",
                SurgeryPriority.ROUTINE,REQUESTED_AT,actor,CORRELATION_ID);
        surgeryCase.beginPreop(actor,CORRELATION_ID,REQUESTED_AT.plusSeconds(1));
        stubNewReceipt();
        when(admissions.findAdmission(admissionId,CORRELATION_ID)).thenReturn(
                new com.mediflow.surgery.application.port.out.AdmissionLookupPort.AdmissionSnapshot(admissionId,
                        UUID.randomUUID(),surgeryCase.getDepartmentId(),UUID.randomUUID(),"ADMITTED",
                        OrganizationLookupPort.ReferenceState.ACTIVE,"1",NOW));
        assertThatThrownBy(()->service.prepare(command(surgeryCase,actor,1,0)))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code","SURGERY_ADMISSION_RELATIONSHIP_MISMATCH");
        when(admissions.findAdmission(admissionId,CORRELATION_ID)).thenReturn(
                new com.mediflow.surgery.application.port.out.AdmissionLookupPort.AdmissionSnapshot(admissionId,
                        surgeryCase.getPatientId(),UUID.randomUUID(),UUID.randomUUID(),"ADMITTED",
                        OrganizationLookupPort.ReferenceState.ACTIVE,"1",NOW));
        assertThatThrownBy(()->service.prepare(command(surgeryCase,actor,1,0)))
                .hasFieldOrPropertyWithValue("code","SURGERY_ADMISSION_RELATIONSHIP_MISMATCH");
        verifyNoInteractions(organization,schedules);
        verify(cases,never()).lockById(any());
    }

    @Test
    void prepare_medicallyDischargedAdmission_rejectsWithoutDraftMutation() {
        UUID admissionId=UUID.randomUUID();
        surgeryCase=SurgeryCase.create(UUID.randomUUID(),UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.ADMISSION,admissionId,admissionId,null),
                UUID.randomUUID(),UUID.randomUUID(),actor.verifiedStaffId(),"PROC-001","Indication",
                SurgeryPriority.ROUTINE,REQUESTED_AT,actor,CORRELATION_ID);
        surgeryCase.beginPreop(actor,CORRELATION_ID,REQUESTED_AT.plusSeconds(1));
        stubNewReceipt();
        when(admissions.findAdmission(admissionId,CORRELATION_ID)).thenReturn(
                new com.mediflow.surgery.application.port.out.AdmissionLookupPort.AdmissionSnapshot(admissionId,
                        surgeryCase.getPatientId(),surgeryCase.getDepartmentId(),UUID.randomUUID(),"MEDICALLY_DISCHARGED",
                        OrganizationLookupPort.ReferenceState.INACTIVE,"2",NOW));
        assertThatThrownBy(()->service.prepare(command(surgeryCase,actor,1,0)))
                .hasFieldOrPropertyWithValue("code","SURGERY_ADMISSION_INELIGIBLE");
        verifyNoInteractions(organization,schedules);
        verify(cases,never()).lockById(any());
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

    private OrganizationLookupPort.OrganizationLookupSnapshot active(
            OrganizationLookupPort.ReferenceKind kind, UUID id) {
        return new OrganizationLookupPort.OrganizationLookupSnapshot(kind, id,
                OrganizationLookupPort.ReferenceState.ACTIVE, NOW, "org-rev-1",
                kind == OrganizationLookupPort.ReferenceKind.STAFF ? "UNMAPPED_JOB_TITLE" : null,
                null, kind == OrganizationLookupPort.ReferenceKind.ROOM ? surgeryCase.getDepartmentId() : null);
    }

    private OrganizationLookupPort.SurgicalEligibilitySnapshot eligibility(UUID staffId,
            SurgeryTeamRole role, OrganizationLookupPort.ReferenceState state) {
        return new OrganizationLookupPort.SurgicalEligibilitySnapshot(staffId, role, state,
                surgeryCase.getDepartmentId(), NOW, "1", NOW.plusSeconds(3600), NOW.plusSeconds(5400));
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
