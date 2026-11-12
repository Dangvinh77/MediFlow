package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.authority.RecordReadDecision;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.response.RecordCapabilitiesDTO;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.port.out.RecordAccessDeclarationPort;
import com.mediflow.clinical.application.port.out.RecordAccessLogPort;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.application.port.out.RecordAmendmentRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordHandoverRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordReviewAuthorityPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.domain.model.RecordHandover;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordDisposition;

class RecordCapabilitiesTest {
    private static final Instant CREATED = Instant.parse("2026-11-01T03:00:00Z");
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID DEPARTMENT = UUID.randomUUID();
    private static final UUID ASSIGNED_DOCTOR = UUID.randomUUID();
    private static final UUID APPOINTMENT = UUID.randomUUID();

    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    private final RecordReadPolicy readPolicy = mock(RecordReadPolicy.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);

    @BeforeEach
    void setUp() {
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());
        when(readPolicy.forRecord(any(), any(), any(), any())).thenReturn(RecordReadDecision.allow("CARE_ASSIGNMENT"));
    }

    @Test
    void assignedDoctorInExamCanWrite() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        actor(ASSIGNED_DOCTOR, "DOCTOR");

        RecordCapabilitiesDTO capabilities = service(true, true).capabilities(record.getRecordId());

        assertThat(capabilities.patientId()).isEqualTo(record.getPatientId());
        assertThat(capabilities.canRead()).isTrue();
        assertThat(capabilities.readBasis()).isEqualTo("CARE_ASSIGNMENT");
        assertThat(capabilities.canWriteContent()).isTrue();
        assertThat(capabilities.canComplete()).isTrue();
        assertThat(capabilities.canRequestAmendment()).isFalse();
        assertThat(capabilities.writeBlockedReason()).isNull();
    }

    @Test
    void otherDoctorSameDepartmentCannotWrite() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        actor(UUID.randomUUID(), "DOCTOR");

        RecordCapabilitiesDTO capabilities = service(true, true).capabilities(record.getRecordId());

        assertThat(capabilities.canWriteContent()).isFalse();
        assertThat(capabilities.canComplete()).isFalse();
        assertThat(capabilities.writeBlockedReason()).isEqualTo("NOT_ASSIGNED_DOCTOR");
    }

    @Test
    void assignedDoctorOutsideExamCannotWrite() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.READY_FOR_EXAM);
        actor(ASSIGNED_DOCTOR, "DOCTOR");

        RecordCapabilitiesDTO capabilities = service(true, true).capabilities(record.getRecordId());

        assertThat(capabilities.canWriteContent()).isFalse();
        assertThat(capabilities.writeBlockedReason()).isEqualTo("NOT_IN_EXAM");
    }

    @Test
    void completedRecordCannotBeWrittenByAuthor() {
        MedicalRecord record = store(MedicalRecord.restore(UUID.randomUUID(), PATIENT, ASSIGNED_DOCTOR, DEPARTMENT,
                LocalDate.of(2026, 11, 1), "symptoms", APPOINTMENT, List.of(Diagnosis.create("Viem hong", "mo ta", "J02")), CREATED, CREATED,
                MedicalRecordStatus.COMPLETED, RecordDisposition.values()[0], null, CREATED));
        appointment(AppointmentStatus.COMPLETED);
        actor(ASSIGNED_DOCTOR, "DOCTOR");

        RecordCapabilitiesDTO capabilities = service(true, true).capabilities(record.getRecordId());

        assertThat(capabilities.canWriteContent()).isFalse();
        assertThat(capabilities.canComplete()).isFalse();
        assertThat(capabilities.writeBlockedReason()).isEqualTo("RECORD_COMPLETED");
    }

    @Test
    void deniedReaderReportsNoReadCapability() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        actor(UUID.randomUUID(), "NURSE");
        when(readPolicy.forRecord(any(), any(), any(), any()))
                .thenReturn(RecordReadDecision.deny("RECORD_ACCESS_PURPOSE_REQUIRED"));

        RecordCapabilitiesDTO capabilities = service(true, true).capabilities(record.getRecordId());

        assertThat(capabilities.canRead()).isFalse();
        assertThat(capabilities.readBasis()).isNull();
        assertThat(capabilities.canWriteContent()).isFalse();
    }

    @Test
    void flagOffReportsLegacyRole() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        actor(UUID.randomUUID(), "DOCTOR");

        RecordCapabilitiesDTO capabilities = service(false, false).capabilities(record.getRecordId());

        assertThat(capabilities.canRead()).isTrue();
        assertThat(capabilities.readBasis()).isEqualTo("LEGACY_ROLE");
        assertThat(capabilities.canWriteContent()).isTrue();
    }

    private final RecordHandoverRepositoryPort handovers = mock(RecordHandoverRepositoryPort.class);
    private final RecordAmendmentRepositoryPort amendments = mock(RecordAmendmentRepositoryPort.class);
    private final StaffLookupPort staff = mock(StaffLookupPort.class);
    private final SchedulingAuthorityPort authority = mock(SchedulingAuthorityPort.class);
    private final RecordReviewAuthorityPort review = mock(RecordReviewAuthorityPort.class);

    private RecordAccessApplicationService collaborative() {
        org.mockito.Mockito.lenient().when(staff.departmentOf(any())).thenReturn(Optional.of(DEPARTMENT));
        org.mockito.Mockito.lenient().when(handovers.findPending(any())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(amendments.findAssignment(any(), any())).thenReturn(Optional.empty());
        return new RecordAccessApplicationService(records, appointments, mock(RecordAccessDeclarationPort.class),
                mock(RecordAccessLogPort.class), readPolicy, actors, correlations, new OutpatientMvpMode(true),
                new RecordAccessPolicyMode(true), Optional.of(new RecordCollaborationCapabilities(handovers, amendments,
                        staff, authority, review, correlations)));
    }

    private MedicalRecord completedRecord() {
        return store(MedicalRecord.restore(UUID.randomUUID(), PATIENT, ASSIGNED_DOCTOR, DEPARTMENT,
                LocalDate.of(2026, 11, 1), "symptoms", APPOINTMENT, List.of(Diagnosis.create("Viem hong", "mo ta", "J02")),
                CREATED, CREATED, MedicalRecordStatus.COMPLETED, RecordDisposition.values()[0], null, CREATED));
    }

    @Test
    void capabilitiesAbsentWhenAmendmentFlagOff() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        actor(ASSIGNED_DOCTOR, "DOCTOR");

        RecordCapabilitiesDTO capabilities = service(true, true).capabilities(record.getRecordId());

        assertThat(capabilities.canHandover()).isNull();
        assertThat(capabilities.canAcceptHandover()).isNull();
        assertThat(capabilities.canAddConsultationNote()).isNull();
        assertThat(capabilities.canAmend()).isNull();
        assertThat(capabilities.canDecideAmendments()).isNull();
    }

    @Test
    void responsibleDoctorCanHandoverAndAddNote() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        actor(ASSIGNED_DOCTOR, "DOCTOR");

        RecordCapabilitiesDTO capabilities = collaborative().capabilities(record.getRecordId());

        assertThat(capabilities.canHandover()).isTrue();
        assertThat(capabilities.canAddConsultationNote()).isTrue();
        assertThat(capabilities.canAcceptHandover()).isFalse();
        assertThat(capabilities.canAmend()).isFalse();
    }

    @Test
    void pendingHandoverBlocksAnotherAndReceiverCanAccept() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        UUID receiver = UUID.randomUUID();
        RecordAccessApplicationService service = collaborative();
        when(handovers.findPending(record.getRecordId())).thenReturn(Optional.of(
                RecordHandover.request(record.getRecordId(), ASSIGNED_DOCTOR, receiver, "Het ca", "Cho ket qua", CREATED)));

        actor(ASSIGNED_DOCTOR, "DOCTOR");
        assertThat(service.capabilities(record.getRecordId()).canHandover()).isTrue();
        actor(receiver, "DOCTOR");
        assertThat(service.capabilities(record.getRecordId()).canAcceptHandover()).isTrue();
    }

    @Test
    void finishedRecordAuthorCanAmend() {
        MedicalRecord record = completedRecord();
        appointment(AppointmentStatus.COMPLETED);
        actor(ASSIGNED_DOCTOR, "DOCTOR");

        RecordCapabilitiesDTO capabilities = collaborative().capabilities(record.getRecordId());

        assertThat(capabilities.canAmend()).isTrue();
        assertThat(capabilities.canRequestAmendment()).isFalse();
        assertThat(capabilities.canApplyAmendment()).isTrue();
        assertThat(capabilities.canHandover()).isFalse();
        assertThat(capabilities.canAddConsultationNote()).isFalse();
    }

    @Test
    void headAssignedDoctorRequestsIndependentReviewInsteadOfApplyingDirectly() {
        MedicalRecord record = completedRecord();
        appointment(AppointmentStatus.COMPLETED);
        RecordAccessApplicationService service = collaborative();
        UUID assigned = UUID.randomUUID();
        actor(assigned, "DOCTOR");
        when(amendments.findAssignment(record.getRecordId(), assigned)).thenReturn(Optional.of(
                com.mediflow.clinical.domain.model.AmendmentAssignment.create(record.getRecordId(), assigned,
                        UUID.randomUUID(), "Bác sĩ phụ trách đã chuyển khoa", CREATED)));

        RecordCapabilitiesDTO capabilities = service.capabilities(record.getRecordId());

        assertThat(capabilities.canAmend()).isTrue();
        assertThat(capabilities.canRequestAmendment()).isTrue();
        assertThat(capabilities.canApplyAmendment()).isFalse();
    }

    @Test
    void headCanDecideAmendments() {
        MedicalRecord record = completedRecord();
        appointment(AppointmentStatus.COMPLETED);
        UUID head = UUID.randomUUID();
        actor(head, "DOCTOR");
        when(authority.authority(any(), any(), any())).thenReturn(new SchedulingAuthoritySnapshot(UUID.randomUUID(), true,
                true, "DOCTOR", head, true, DEPARTMENT, true, true, 1L, CREATED));

        RecordCapabilitiesDTO capabilities = collaborative().capabilities(record.getRecordId());

        assertThat(capabilities.canDecideAmendments()).isTrue();
        assertThat(capabilities.canAmend()).isFalse();
        assertThat(capabilities.canApplyAmendment()).isFalse();
    }

    @Test
    void recordReviewerCanDecideAmendments() {
        MedicalRecord record = completedRecord();
        appointment(AppointmentStatus.COMPLETED);
        actor(UUID.randomUUID(), "DOCTOR");
        when(review.canReviewRecords(any(), any())).thenReturn(true);

        assertThat(collaborative().capabilities(record.getRecordId()).canDecideAmendments()).isTrue();
    }

    @Test
    void authorityOutageHidesDecisionWithoutFailingCapabilities() {
        MedicalRecord record = completedRecord();
        appointment(AppointmentStatus.COMPLETED);
        actor(UUID.randomUUID(), "DOCTOR");
        when(authority.authority(any(), any(), any())).thenThrow(new IllegalStateException("timed out"));
        when(review.canReviewRecords(any(), any())).thenThrow(new IllegalStateException("timed out"));

        assertThat(collaborative().capabilities(record.getRecordId()).canDecideAmendments()).isFalse();
    }

    @Test
    void nurseHasNoCollaborationCapability() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        actor(UUID.randomUUID(), "NURSE");

        RecordCapabilitiesDTO capabilities = collaborative().capabilities(record.getRecordId());

        assertThat(capabilities.canHandover()).isFalse();
        assertThat(capabilities.canAcceptHandover()).isFalse();
        assertThat(capabilities.canAddConsultationNote()).isFalse();
        assertThat(capabilities.canAmend()).isFalse();
        assertThat(capabilities.canDecideAmendments()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(authority, review);
    }

    private RecordAccessApplicationService service(boolean policyEnabled, boolean mvpEnabled) {
        return new RecordAccessApplicationService(records, appointments, mock(RecordAccessDeclarationPort.class),
                mock(RecordAccessLogPort.class), readPolicy, actors, correlations,
                new OutpatientMvpMode(mvpEnabled), new RecordAccessPolicyMode(policyEnabled));
    }

    private MedicalRecord openRecord() {
        return store(MedicalRecord.restore(UUID.randomUUID(), PATIENT, ASSIGNED_DOCTOR, DEPARTMENT,
                LocalDate.of(2026, 11, 1), "symptoms", APPOINTMENT, List.<Diagnosis>of(), CREATED, null));
    }

    private MedicalRecord store(MedicalRecord record) {
        when(records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        return record;
    }

    private void appointment(AppointmentStatus status) {
        when(appointments.findById(APPOINTMENT)).thenReturn(Optional.of(Appointment.restore(APPOINTMENT, PATIENT,
                ASSIGNED_DOCTOR, DEPARTMENT, LocalDate.of(2026, 11, 1), LocalTime.of(9, 0), status, "visit", CREATED,
                null, (short) 0, UUID.randomUUID(), CREATED, null, null, null, null,
                status == AppointmentStatus.COMPLETED ? CREATED : null)));
    }

    private void actor(UUID staffId, String role) {
        when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), staffId, null, role));
    }
}
