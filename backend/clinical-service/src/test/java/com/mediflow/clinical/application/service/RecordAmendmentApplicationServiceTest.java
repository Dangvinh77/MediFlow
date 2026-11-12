package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.AmendmentAssignmentRequest;
import com.mediflow.clinical.application.dto.request.AmendmentDecisionRequest;
import com.mediflow.clinical.application.dto.request.RecordAmendmentRequest;
import com.mediflow.clinical.application.port.out.ClinicalEventPublisherPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordAmendmentRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordAmendmentRepositoryPort.PendingAmendment;
import com.mediflow.clinical.application.port.out.RecordReviewAuthorityPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.model.AmendmentAssignment;
import com.mediflow.clinical.domain.model.AmendmentSection;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordAmendment;
import com.mediflow.clinical.domain.model.RecordAmendmentStatus;
import com.mediflow.clinical.domain.model.RecordDisposition;
import com.mediflow.common.exception.ForbiddenOperationException;

class RecordAmendmentApplicationServiceTest {
    static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    final UUID department = UUID.randomUUID();
    final UUID otherDepartment = UUID.randomUUID();
    final UUID author = UUID.randomUUID();
    final UUID head = UUID.randomUUID();
    final UUID reviewer = UUID.randomUUID();
    final UUID colleague = UUID.randomUUID();

    final RecordAmendmentRepositoryPort amendments = mock(RecordAmendmentRepositoryPort.class);
    final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    final StaffLookupPort staff = mock(StaffLookupPort.class);
    final SchedulingAuthorityPort authority = mock(SchedulingAuthorityPort.class);
    final RecordReviewAuthorityPort review = mock(RecordReviewAuthorityPort.class);
    final RecordReadGuard guard = mock(RecordReadGuard.class);
    final IdempotentCommandPort idempotency = mock(IdempotentCommandPort.class);
    final CorrelationIdProvider correlationIds = mock(CorrelationIdProvider.class);
    final RecordAmendmentApplicationService service = new RecordAmendmentApplicationService(amendments, records, actors,
            staff, authority, review, guard, true, idempotency, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")), correlationIds);
    final MedicalRecord record = MedicalRecord.restore(UUID.randomUUID(), UUID.randomUUID(), author, department,
            LocalDate.parse("2026-11-09"), "Sốt", UUID.randomUUID(),
            List.of(Diagnosis.create("Viêm phổi", "Thùy dưới phải", "J18")), NOW, NOW, MedicalRecordStatus.COMPLETED,
            RecordDisposition.PRESCRIPTION, "Kê đơn", NOW);

    static UUID account(UUID staffId) {
        return UUID.nameUUIDFromBytes(("acct-" + staffId).getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach void setUp() {
        as(author, "DOCTOR");
        lenient().when(correlationIds.currentOrCreate()).thenReturn(UUID.randomUUID());
        lenient().when(staff.departmentOf(any())).thenReturn(Optional.of(department));
        lenient().when(staff.departmentOf(reviewer)).thenReturn(Optional.of(otherDepartment));
        lenient().when(authority.authority(any(), any(), anyString())).thenAnswer(call ->
                snapshot(null, call.getArgument(1), false));
        lenient().when(authority.authority(eq(account(head)), eq(department), anyString()))
                .thenReturn(snapshot(head, department, true));
        lenient().when(review.canReviewRecords(eq(account(reviewer)), any())).thenReturn(true);
        lenient().when(records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        lenient().when(records.findByIdForUpdate(record.getRecordId())).thenReturn(Optional.of(record));
        lenient().when(amendments.findAssignment(any(), any())).thenReturn(Optional.empty());
        lenient().when(amendments.findByRecord(any())).thenReturn(List.of());
        lenient().when(idempotency.execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(),
                any(), any())).thenAnswer(call -> new IdempotentCommandPort.Result<>(
                        ((Supplier<?>) call.getArgument(8)).get(), call.getArgument(6), null, false));
    }

    private static SchedulingAuthoritySnapshot snapshot(UUID staffId, UUID dept, boolean manages) {
        return new SchedulingAuthoritySnapshot(staffId == null ? null : account(staffId), true, true, "DOCTOR", staffId,
                true, dept, true, manages, 1L, NOW);
    }

    private void as(UUID staffId, String role) {
        lenient().when(actors.current()).thenReturn(new ActorIdentity(account(staffId), staffId, null, role));
    }

    private RecordAmendmentRequest symptoms() {
        return new RecordAmendmentRequest(AmendmentSection.SYMPTOMS, "Sốt cao 39 độ", "Ghi thiếu nhiệt độ");
    }

    private RecordAmendment pendingBy(UUID by) {
        RecordAmendment amendment = RecordAmendment.request(record.getRecordId(), AmendmentSection.SYMPTOMS, "Sốt",
                "Sốt cao", "Ghi thiếu", by, null, NOW);
        lenient().when(amendments.findByIdForUpdate(amendment.getAmendmentId())).thenReturn(Optional.of(amendment));
        return amendment;
    }

    private RecordAmendment captureInsert() {
        ArgumentCaptor<RecordAmendment> saved = ArgumentCaptor.forClass(RecordAmendment.class);
        verify(amendments).insert(saved.capture());
        return saved.getValue();
    }

    private Object decide(RecordAmendment amendment, String decision) {
        return service.decide(record.getRecordId(), amendment.getAmendmentId(),
                new AmendmentDecisionRequest(decision, "Xem xét"), "k").data();
    }

    @Test void openRecordIsNotAmended() {
        MedicalRecord open = MedicalRecord.openForAppointment(UUID.randomUUID(), author, department,
                LocalDate.parse("2026-11-10"), "Sốt", UUID.randomUUID(), NOW);
        when(records.findByIdForUpdate(open.getRecordId())).thenReturn(Optional.of(open));
        assertThatThrownBy(() -> service.request(open.getRecordId(), symptoms(), "k"))
                .extracting("code").isEqualTo("AMENDMENT_RECORD_OPEN");
    }

    @Test void amendmentSnapshotsOriginalText() {
        var result = service.request(record.getRecordId(), symptoms(), "k");
        assertThat(result.status()).isEqualTo(201);
        RecordAmendment saved = captureInsert();
        assertThat(saved.getOriginalText()).isEqualTo("Sốt");
        assertThat(saved.getAuthorStaffId()).isEqualTo(author);
        assertThat(saved.getAssignedByStaffId()).isNull();
    }

    @Test void responsibleDoctorAppliesCompletedRecordCorrectionWithAnExplicitAuditDecision() {
        var result = service.request(record.getRecordId(), symptoms(), "k");

        assertThat(result.data().status()).isEqualTo("APPLIED");
        RecordAmendment saved = captureInsert();
        assertThat(saved.getReason()).isEqualTo("Ghi thiếu nhiệt độ");
        assertThat(saved.getAuthorStaffId()).isEqualTo(author);
        assertThat(saved.getStatus().name()).isEqualTo("APPLIED");
        assertThat(saved.getDecidedByStaffId()).isEqualTo(author);
        assertThat(saved.getDecidedAt()).isEqualTo(NOW);
        assertThat(saved.getDecisionNote()).containsIgnoringCase("responsible doctor");
        assertThat(saved.getContentHash()).isEqualTo(saved.contentHash());
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.COMPLETED);
        assertThat(record.getSymptoms()).isEqualTo("Sốt");
        verify(records, never()).save(any());
    }

    @Test void headAssignedDoctorStillNeedsIndependentReviewForAnotherDoctorsRecord() {
        when(staff.departmentOf(author)).thenReturn(Optional.empty());
        when(amendments.findAssignment(record.getRecordId(), colleague)).thenReturn(Optional.of(
                AmendmentAssignment.create(record.getRecordId(), colleague, head, "Bác sĩ phụ trách đã chuyển khoa", NOW)));
        as(colleague, "DOCTOR");

        service.request(record.getRecordId(), symptoms(), "k");

        RecordAmendment saved = captureInsert();
        assertThat(saved.getStatus().name()).isEqualTo("PENDING");
        assertThat(saved.getDecidedByStaffId()).isNull();
        assertThat(saved.getAssignedByStaffId()).isEqualTo(head);
    }

    @Test void nextCorrectionSnapshotsTheLatestAcceptedValueAndRejectsAStaleOriginal() {
        RecordAmendment earlier = RecordAmendment.request(record.getRecordId(), AmendmentSection.SYMPTOMS, "Sốt",
                "Sốt cao 39 độ", "Bổ sung nhiệt độ", author, null, NOW);
        earlier.applyByResponsibleDoctor(author, NOW);
        when(amendments.findByRecord(record.getRecordId())).thenReturn(List.of(earlier));

        service.request(record.getRecordId(),
                new RecordAmendmentRequest(AmendmentSection.SYMPTOMS, "Sốt cao về chiều", "Bổ sung thời điểm",
                        "Sốt cao 39 độ"), "k");
        assertThat(captureInsert().getOriginalText()).isEqualTo("Sốt cao 39 độ");

        assertThatThrownBy(() -> service.request(record.getRecordId(),
                new RecordAmendmentRequest(AmendmentSection.SYMPTOMS, "Sốt cao", "Sửa lại",
                        "Sốt"), "k2"))
                .extracting("code").isEqualTo("AMENDMENT_STALE_SOURCE");
    }

    @Test void approvalCannotApplyARequestAgainstAValueThatHasSinceChanged() {
        RecordAmendment pending = RecordAmendment.request(record.getRecordId(), AmendmentSection.SYMPTOMS, "Sốt",
                "Sốt nhẹ", "Sửa mức độ", colleague, head, NOW);
        when(amendments.findByIdForUpdate(pending.getAmendmentId())).thenReturn(Optional.of(pending));
        RecordAmendment newer = RecordAmendment.request(record.getRecordId(), AmendmentSection.SYMPTOMS, "Sốt",
                "Sốt cao 39 độ", "Bổ sung nhiệt độ", author, null, NOW);
        newer.applyByResponsibleDoctor(author, NOW);
        when(amendments.findByRecord(record.getRecordId())).thenReturn(List.of(newer, pending));
        as(head, "DOCTOR");

        assertThatThrownBy(() -> decide(pending, "APPROVED"))
                .extracting("code").isEqualTo("AMENDMENT_STALE_SOURCE");
        assertThat(pending.getStatus()).isEqualTo(RecordAmendmentStatus.PENDING);
        verify(amendments, never()).decide(any());
    }

    @Test void diagnosisSectionSnapshotsDiagnosisList() {
        service.request(record.getRecordId(), new RecordAmendmentRequest(AmendmentSection.DIAGNOSIS,
                "J15 Viêm phổi do vi khuẩn", "Cấy đờm dương tính"), "k");
        assertThat(captureInsert().getOriginalText()).contains("J18").contains("Viêm phổi").contains("Thùy dưới phải");
    }

    @Test void onlyResponsibleDoctorRequests() {
        as(colleague, "DOCTOR");
        assertThatThrownBy(() -> service.request(record.getRecordId(), symptoms(), "k"))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(amendments, never()).insert(any());
    }

    @Test void departedAuthorNeedsAssignment() {
        when(staff.departmentOf(author)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request(record.getRecordId(), symptoms(), "k"))
                .isInstanceOf(ForbiddenOperationException.class).extracting("code").isEqualTo("AMENDMENT_AUTHOR_NOT_ELIGIBLE");
    }

    @Test void headAssignsDoctorWhenAuthorDeparted() {
        when(staff.departmentOf(author)).thenReturn(Optional.of(otherDepartment));
        as(head, "DOCTOR");
        var result = service.assign(record.getRecordId(), new AmendmentAssignmentRequest(colleague, "BS cũ đã chuyển khoa"), "k");
        assertThat(result.status()).isEqualTo(201);
        ArgumentCaptor<AmendmentAssignment> saved = ArgumentCaptor.forClass(AmendmentAssignment.class);
        verify(amendments).insertAssignment(saved.capture());
        assertThat(saved.getValue().getAssigneeStaffId()).isEqualTo(colleague);
        assertThat(saved.getValue().getAssignedByStaffId()).isEqualTo(head);
    }

    @Test void onlyHeadAssigns() {
        when(staff.departmentOf(author)).thenReturn(Optional.empty());
        as(colleague, "DOCTOR");
        assertThatThrownBy(() -> service.assign(record.getRecordId(), new AmendmentAssignmentRequest(colleague, "Lý do"), "k"))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test void assignmentRefusedWhileAuthorActive() {
        as(head, "DOCTOR");
        assertThatThrownBy(() -> service.assign(record.getRecordId(), new AmendmentAssignmentRequest(colleague, "Lý do"), "k"))
                .extracting("code").isEqualTo("AMENDMENT_INVALID");
        verify(amendments, never()).insertAssignment(any());
    }

    @Test void headRevokesAnAssignment() {
        UUID assignmentId = UUID.randomUUID();
        when(amendments.assignmentExists(record.getRecordId(), assignmentId)).thenReturn(true);
        as(head, "DOCTOR");
        service.revokeAssignment(record.getRecordId(), assignmentId);
        verify(amendments).revokeAssignment(record.getRecordId(), assignmentId, head, NOW);
    }

    @Test void onlyHeadRevokesAnAssignment() {
        UUID assignmentId = UUID.randomUUID();
        when(amendments.assignmentExists(record.getRecordId(), assignmentId)).thenReturn(true);
        as(colleague, "DOCTOR");
        assertThatThrownBy(() -> service.revokeAssignment(record.getRecordId(), assignmentId))
                .extracting("code").isEqualTo("AMENDMENT_ASSIGNMENT_DENIED");
        verify(amendments, never()).revokeAssignment(any(), any(), any(), any());
    }

    @Test void revokingAnUnknownAssignmentIsNotFound() {
        as(head, "DOCTOR");
        assertThatThrownBy(() -> service.revokeAssignment(record.getRecordId(), UUID.randomUUID()))
                .extracting("code").isEqualTo("AMENDMENT_ASSIGNMENT_NOT_FOUND");
    }

    @Test void assigneeRequestsWithOwnIdentity() {
        when(staff.departmentOf(author)).thenReturn(Optional.empty());
        when(amendments.findAssignment(record.getRecordId(), colleague)).thenReturn(Optional.of(
                AmendmentAssignment.create(record.getRecordId(), colleague, head, "BS cũ nghỉ", NOW)));
        as(colleague, "DOCTOR");
        service.request(record.getRecordId(), symptoms(), "k");
        RecordAmendment saved = captureInsert();
        assertThat(saved.getAuthorStaffId()).isEqualTo(colleague);
        assertThat(saved.getAssignedByStaffId()).isEqualTo(head);
    }

    @Test void headApproves() {
        RecordAmendment amendment = pendingBy(author);
        as(head, "DOCTOR");
        decide(amendment, "APPROVED");
        assertThat(amendment.getStatus()).isEqualTo(RecordAmendmentStatus.APPROVED);
        assertThat(amendment.getDecidedByStaffId()).isEqualTo(head);
        verify(amendments).decide(amendment);
    }

    @Test void headRejectsWithNote() {
        RecordAmendment amendment = pendingBy(author);
        as(head, "DOCTOR");
        decide(amendment, "REJECTED");
        assertThat(amendment.getStatus()).isEqualTo(RecordAmendmentStatus.REJECTED);
    }

    @Test void authorCannotDecideOwnAmendment() {
        RecordAmendment amendment = pendingBy(head);
        as(head, "DOCTOR");
        assertThatThrownBy(() -> decide(amendment, "APPROVED")).extracting("code").isEqualTo("AMENDMENT_SELF_DECISION");
        verify(amendments, never()).decide(any());
    }

    @Test void recordReviewerApprovesHeadAuthoredAmendment() {
        RecordAmendment amendment = pendingBy(head);
        as(reviewer, "DOCTOR");
        decide(amendment, "APPROVED");
        assertThat(amendment.getDecidedByStaffId()).isEqualTo(reviewer);
    }

    @Test void recordReviewerFromOtherDepartmentMayDecide() {
        RecordAmendment amendment = pendingBy(author);
        as(reviewer, "DOCTOR");
        decide(amendment, "REJECTED");
        assertThat(amendment.getStatus()).isEqualTo(RecordAmendmentStatus.REJECTED);
    }

    @Test void reviewerWhoIsAuthorCannotDecide() {
        RecordAmendment amendment = pendingBy(reviewer);
        as(reviewer, "DOCTOR");
        assertThatThrownBy(() -> decide(amendment, "APPROVED")).extracting("code").isEqualTo("AMENDMENT_SELF_DECISION");
    }

    @Test void revokedReviewerCannotDecide() {
        RecordAmendment amendment = pendingBy(author);
        when(review.canReviewRecords(eq(account(reviewer)), any())).thenReturn(false);
        as(reviewer, "DOCTOR");
        assertThatThrownBy(() -> decide(amendment, "APPROVED")).isInstanceOf(ForbiddenOperationException.class);
        assertThat(amendment.getStatus()).isEqualTo(RecordAmendmentStatus.PENDING);
    }

    @Test void adminWithoutCapabilityCannotDecide() {
        RecordAmendment amendment = pendingBy(author);
        as(UUID.randomUUID(), "ADMIN");
        assertThatThrownBy(() -> decide(amendment, "APPROVED")).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test void otherDepartmentHeadCannotDecide() {
        UUID otherHead = UUID.randomUUID();
        when(authority.authority(eq(account(otherHead)), eq(otherDepartment), anyString()))
                .thenReturn(snapshot(otherHead, otherDepartment, true));
        RecordAmendment amendment = pendingBy(author);
        as(otherHead, "DOCTOR");
        assertThatThrownBy(() -> decide(amendment, "APPROVED")).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test void pendingQueueListsOnlyWhatCallerMayDecide() {
        RecordAmendment byAuthor = pendingBy(author);
        RecordAmendment byHead = pendingBy(head);
        RecordAmendment elsewhere = RecordAmendment.request(UUID.randomUUID(), AmendmentSection.ADVICE, "", "Uống nhiều nước",
                "Bổ sung", UUID.randomUUID(), null, NOW);
        RecordAmendment byReviewer = pendingBy(reviewer);
        when(amendments.findPending()).thenReturn(List.of(new PendingAmendment(byAuthor, department),
                new PendingAmendment(byHead, department), new PendingAmendment(elsewhere, otherDepartment),
                new PendingAmendment(byReviewer, department)));
        as(head, "DOCTOR");
        assertThat(service.pending()).extracting("amendmentId")
                .containsExactly(byAuthor.getAmendmentId(), byReviewer.getAmendmentId());
        as(reviewer, "DOCTOR");
        assertThat(service.pending()).extracting("amendmentId")
                .containsExactly(byAuthor.getAmendmentId(), byHead.getAmendmentId(), elsewhere.getAmendmentId());
        as(colleague, "DOCTOR");
        assertThat(service.pending()).isEmpty();
    }

    @Test void pendingQueueLogsAReviewReadForEachListedRecord() {
        RecordAmendment byAuthor = pendingBy(author);
        when(amendments.findPending()).thenReturn(List.of(new PendingAmendment(byAuthor, department)));
        as(head, "DOCTOR");
        assertThat(service.pending()).hasSize(1);
        org.mockito.Mockito.verify(guard).logReviewRead(record.getPatientId(), record.getRecordId());
        as(colleague, "DOCTOR");
        org.mockito.Mockito.clearInvocations(guard);
        assertThat(service.pending()).isEmpty();
        org.mockito.Mockito.verify(guard, org.mockito.Mockito.never()).logReviewRead(any(), any());
    }

    @Test void decisionIsFinal() {
        RecordAmendment amendment = pendingBy(author);
        as(head, "DOCTOR");
        decide(amendment, "APPROVED");
        assertThatThrownBy(() -> decide(amendment, "REJECTED")).extracting("code").isEqualTo("AMENDMENT_ALREADY_DECIDED");
    }

    @Test void unknownDecisionIsInvalid() {
        RecordAmendment amendment = pendingBy(author);
        as(head, "DOCTOR");
        assertThatThrownBy(() -> decide(amendment, "MAYBE")).extracting("code").isEqualTo("AMENDMENT_INVALID");
    }

    @Test void approvedAmendmentLeavesOriginalAndContractFieldsUnchanged() {
        RecordAmendment amendment = pendingBy(author);
        as(head, "DOCTOR");
        decide(amendment, "APPROVED");
        assertThat(record.getSymptoms()).isEqualTo("Sốt");
        assertThat(record.getDoctorId()).isEqualTo(author);
        assertThat(record.getCompletedAt()).isEqualTo(NOW);
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.COMPLETED);
        verify(records, never()).save(any());
    }

    @Test void approvedAmendmentTouchesNoAttachedResult() {
        assertThat(Arrays.stream(RecordAmendmentApplicationService.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .doesNotContain(ExternalResultRepositoryPort.class, ClinicalEventPublisherPort.class);
    }

    @Test void authorityTimeoutRefusesDecision() {
        RecordAmendment amendment = pendingBy(author);
        when(authority.authority(eq(account(head)), eq(department), anyString()))
                .thenThrow(new IllegalStateException("Organization timed out"));
        as(head, "DOCTOR");
        assertThatThrownBy(() -> decide(amendment, "APPROVED")).isInstanceOf(IllegalStateException.class);
        verify(amendments, never()).decide(any());
        assertThat(amendment.getStatus()).isEqualTo(RecordAmendmentStatus.PENDING);
    }

    @Test void reviewAuthorityTimeoutRefusesDecision() {
        RecordAmendment amendment = pendingBy(author);
        when(review.canReviewRecords(eq(account(reviewer)), any())).thenThrow(new IllegalStateException("timed out"));
        as(reviewer, "DOCTOR");
        assertThatThrownBy(() -> decide(amendment, "APPROVED")).isInstanceOf(IllegalStateException.class);
        verify(amendments, never()).decide(any());
    }

    @Test void listNeedsRecordRead() {
        when(amendments.findByRecord(record.getRecordId())).thenReturn(List.of());
        as(UUID.randomUUID(), "NURSE");
        assertThat(service.list(record.getRecordId())).isEmpty();
        verify(guard).requireRecord(record);
        verifyNoInteractions(review);
    }
}
