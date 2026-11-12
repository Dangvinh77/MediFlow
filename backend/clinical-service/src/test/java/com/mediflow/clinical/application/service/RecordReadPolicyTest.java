package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.authority.RecordReadDecision;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordAccessDeclarationPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.RecordAccessDeclaration;

class RecordReadPolicyTest {
    private static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");
    private static final String CORRELATION = "corr-read";
    private static final UUID CARDIOLOGY = UUID.randomUUID();
    private static final UUID GASTRO = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();

    private static final UUID DOCTOR_B = UUID.randomUUID();
    private static final UUID DOCTOR_C = UUID.randomUUID();
    private static final UUID DOCTOR_D = UUID.randomUUID();
    private static final UUID DOCTOR_E = UUID.randomUUID();

    private final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    private final RecordAccessDeclarationPort declarations = mock(RecordAccessDeclarationPort.class);
    private final SchedulingAuthorityPort authority = mock(SchedulingAuthorityPort.class);
    private final List<Appointment> patientAppointments = new ArrayList<>();

    private RecordReadPolicy policy;
    private MedicalRecord r1;
    private MedicalRecord r3;

    @BeforeEach
    void setUp() {
        policy = new RecordReadPolicy(appointments, declarations, authority);
        when(appointments.findByPatient(PATIENT)).thenReturn(patientAppointments);
        lenient().when(declarations.findActive(any(), any(), any())).thenReturn(Optional.empty());
        r1 = record(DOCTOR_B, CARDIOLOGY);
        r3 = record(DOCTOR_E, GASTRO);
    }

    @Test
    void doctorWithActiveAppointmentReadsOtherDepartmentRecord() {
        ActorIdentity doctor = staff(DOCTOR_C, "DOCTOR");
        core(doctor, "DOCTOR", CARDIOLOGY, true);
        appointment(DOCTOR_C, CARDIOLOGY, AppointmentStatus.PENDING, null);

        assertAllowed(policy.forRecord(doctor, r3, AT, CORRELATION), "CARE_ASSIGNMENT");
    }

    @Test
    void sameDepartmentDoctorReadsThroughDepartmentEncounter() {
        ActorIdentity doctor = staff(DOCTOR_D, "DOCTOR");
        core(doctor, "DOCTOR", CARDIOLOGY, true);
        appointment(DOCTOR_B, CARDIOLOGY, AppointmentStatus.IN_EXAM, null);

        assertAllowed(policy.forRecord(doctor, r1, AT, CORRELATION), "DEPARTMENT_ENCOUNTER");
    }

    @Test
    void adminAssignedAsDoctorReadsWithoutDeclaration() {
        ActorIdentity admin = staff(DOCTOR_C, "ADMIN");
        core(admin, "ADMIN", CARDIOLOGY, true);
        appointment(DOCTOR_C, CARDIOLOGY, AppointmentStatus.READY_FOR_EXAM, null);

        assertAllowed(policy.forRecord(admin, r3, AT, CORRELATION), "CARE_ASSIGNMENT");
    }

    @Test
    void doctorWithoutBasisNeedsPurpose() {
        ActorIdentity doctor = staff(DOCTOR_C, "DOCTOR");
        core(doctor, "DOCTOR", CARDIOLOGY, true);

        assertDenied(policy.forRecord(doctor, r3, AT, CORRELATION), "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void authorReadsOwnRecordWithoutAssignment() {
        ActorIdentity doctor = staff(DOCTOR_E, "DOCTOR");
        core(doctor, "DOCTOR", GASTRO, true);

        assertAllowed(policy.forRecord(doctor, r3, AT, CORRELATION), "AUTHOR");
    }

    @Test
    void authorStillNeedsBasisForPatientList() {
        ActorIdentity doctor = staff(DOCTOR_E, "DOCTOR");
        core(doctor, "DOCTOR", GASTRO, true);

        assertDenied(policy.forPatient(doctor, PATIENT, AT, CORRELATION), "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void appointmentCompletedExactlyThirtyDaysAgoStillCounts() {
        ActorIdentity doctor = staff(DOCTOR_C, "DOCTOR");
        core(doctor, "DOCTOR", CARDIOLOGY, true);
        appointment(DOCTOR_C, CARDIOLOGY, AppointmentStatus.COMPLETED, AT.minus(Duration.ofDays(30)));

        assertAllowed(policy.forPatient(doctor, PATIENT, AT, CORRELATION), "CARE_ASSIGNMENT");
    }

    @Test
    void appointmentCompletedBeyondThirtyDaysNeedsPurpose() {
        ActorIdentity doctor = staff(DOCTOR_C, "DOCTOR");
        core(doctor, "DOCTOR", CARDIOLOGY, true);
        appointment(DOCTOR_C, CARDIOLOGY, AppointmentStatus.COMPLETED, AT.minus(Duration.ofDays(30)).minusSeconds(1));

        assertDenied(policy.forPatient(doctor, PATIENT, AT, CORRELATION), "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void completedAppointmentWithoutCompletionTimeIsNoBasis() {
        ActorIdentity doctor = staff(DOCTOR_C, "DOCTOR");
        core(doctor, "DOCTOR", CARDIOLOGY, true);
        appointment(DOCTOR_C, CARDIOLOGY, AppointmentStatus.COMPLETED, null);

        assertDenied(policy.forPatient(doctor, PATIENT, AT, CORRELATION), "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void cancelledAppointmentIsNoBasis() {
        ActorIdentity doctor = staff(DOCTOR_C, "DOCTOR");
        core(doctor, "DOCTOR", CARDIOLOGY, true);
        appointment(DOCTOR_C, CARDIOLOGY, AppointmentStatus.CANCELLED, null);

        assertDenied(policy.forPatient(doctor, PATIENT, AT, CORRELATION), "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void declaredDoctorReads() {
        ActorIdentity doctor = staff(DOCTOR_C, "DOCTOR");
        core(doctor, "DOCTOR", CARDIOLOGY, true);
        declaration(doctor, "CONSULTATION");

        assertAllowed(policy.forRecord(doctor, r3, AT, CORRELATION), "DECLARED_PURPOSE");
    }

    @Test
    void declarationExpiringExactlyNowIsRejected() {
        ActorIdentity doctor = staff(DOCTOR_C, "DOCTOR");
        core(doctor, "DOCTOR", CARDIOLOGY, true);
        when(declarations.findActive(doctor.accountId(), PATIENT, AT)).thenReturn(Optional.empty());

        assertDenied(policy.forRecord(doctor, r3, AT, CORRELATION), "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void adminWithoutDeclarationIsDenied() {
        ActorIdentity admin = new ActorIdentity(UUID.randomUUID(), null, null, "ADMIN");
        coreWithoutStaff(admin);

        assertDenied(policy.forRecord(admin, r1, AT, CORRELATION), "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void adminWithDeclarationReadsAllDepartments() {
        ActorIdentity admin = new ActorIdentity(UUID.randomUUID(), null, null, "ADMIN");
        coreWithoutStaff(admin);
        declaration(admin, "RECORD_MANAGEMENT");

        assertAllowed(policy.forRecord(admin, r1, AT, CORRELATION), "DECLARED_PURPOSE");
        assertAllowed(policy.forRecord(admin, r3, AT, CORRELATION), "DECLARED_PURPOSE");
    }

    @Test
    void nurseWithDepartmentAppointmentReadsOwnDepartment() {
        ActorIdentity nurse = staff(UUID.randomUUID(), "NURSE");
        core(nurse, "NURSE", CARDIOLOGY, true);
        appointment(DOCTOR_B, CARDIOLOGY, AppointmentStatus.ARRIVED, null);

        RecordReadDecision decision = policy.forPatient(nurse, PATIENT, AT, CORRELATION);

        assertAllowed(decision, "DEPARTMENT_ENCOUNTER");
        assertThat(decision.nurseDepartmentId()).isEqualTo(CARDIOLOGY);
    }

    @Test
    void nurseCannotReadOtherDepartmentWithoutEmergency() {
        ActorIdentity nurse = staff(UUID.randomUUID(), "NURSE");
        core(nurse, "NURSE", CARDIOLOGY, true);
        appointment(DOCTOR_B, CARDIOLOGY, AppointmentStatus.ARRIVED, null);

        assertDenied(policy.forRecord(nurse, r3, AT, CORRELATION), "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void nurseEmergencyDeclarationReadsOtherDepartment() {
        ActorIdentity nurse = staff(UUID.randomUUID(), "NURSE");
        core(nurse, "NURSE", CARDIOLOGY, true);
        declaration(nurse, "EMERGENCY");

        RecordReadDecision decision = policy.forRecord(nurse, r3, AT, CORRELATION);

        assertAllowed(decision, "DECLARED_PURPOSE");
        assertThat(decision.nurseDepartmentId()).isNull();
    }

    @Test
    void nurseEmergencyDeclarationWidensListBeyondOwnDepartment() {
        ActorIdentity nurse = staff(UUID.randomUUID(), "NURSE");
        core(nurse, "NURSE", CARDIOLOGY, true);
        appointment(DOCTOR_B, CARDIOLOGY, AppointmentStatus.ARRIVED, null);
        declaration(nurse, "EMERGENCY");

        RecordReadDecision decision = policy.forPatient(nurse, PATIENT, AT, CORRELATION);

        assertAllowed(decision, "DECLARED_PURPOSE");
        assertThat(decision.nurseDepartmentId()).isNull();
    }

    @Test
    void nurseNonEmergencyDeclarationIsNoBasis() {
        ActorIdentity nurse = staff(UUID.randomUUID(), "NURSE");
        core(nurse, "NURSE", CARDIOLOGY, true);
        declaration(nurse, "CONSULTATION");

        assertDenied(policy.forRecord(nurse, r3, AT, CORRELATION), "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void nurseTransferredOutLosesAccessDespiteOldAppointment() {
        ActorIdentity nurse = staff(UUID.randomUUID(), "NURSE");
        core(nurse, "NURSE", GASTRO, true);
        appointment(DOCTOR_B, CARDIOLOGY, AppointmentStatus.ARRIVED, null);

        assertDenied(policy.forRecord(nurse, r1, AT, CORRELATION), "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void inactiveStaffIsDeniedDespiteRoleClaim() {
        ActorIdentity doctor = staff(DOCTOR_E, "DOCTOR");
        core(doctor, "DOCTOR", GASTRO, false);
        declaration(doctor, "EMERGENCY");

        assertDenied(policy.forRecord(doctor, r3, AT, CORRELATION), "RECORD_ACCESS_DENIED");
    }

    @Test
    void roleMismatchWithCoreIsDenied() {
        ActorIdentity doctor = staff(DOCTOR_E, "DOCTOR");
        core(doctor, "NURSE", GASTRO, true);

        assertDenied(policy.forRecord(doctor, r3, AT, CORRELATION), "RECORD_ACCESS_DENIED");
    }

    @Test
    void staffIdDifferentFromCoreIsDenied() {
        ActorIdentity doctor = staff(DOCTOR_E, "DOCTOR");
        when(authority.currentDoctorAuthority(doctor.accountId(), CORRELATION)).thenReturn(
                new CurrentDoctorAuthoritySnapshot(doctor.accountId(), true, true, "DOCTOR", UUID.randomUUID(), true,
                        GASTRO, true, AT));

        assertDenied(policy.forRecord(doctor, r3, AT, CORRELATION), "RECORD_ACCESS_DENIED");
    }

    @Test
    void unsupportedRoleIsDenied() {
        ActorIdentity pharmacist = staff(UUID.randomUUID(), "PHARMACIST");
        core(pharmacist, "PHARMACIST", GASTRO, true);

        assertDenied(policy.forRecord(pharmacist, r3, AT, CORRELATION), "RECORD_ACCESS_DENIED");
    }

    @Test
    void coreTimeoutFailsClosed() {
        ActorIdentity doctor = staff(DOCTOR_E, "DOCTOR");
        when(authority.currentDoctorAuthority(eq(doctor.accountId()), any()))
                .thenThrow(new UpstreamUnavailableException("SCHEDULING_AUTHORITY_UNAVAILABLE", "timeout"));

        assertThatThrownBy(() -> policy.forRecord(doctor, r3, AT, CORRELATION))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    private static ActorIdentity staff(UUID staffId, String role) {
        return new ActorIdentity(UUID.randomUUID(), staffId, null, role);
    }

    private void core(ActorIdentity actor, String coreRole, UUID department, boolean staffActive) {
        when(authority.currentDoctorAuthority(actor.accountId(), CORRELATION)).thenReturn(
                new CurrentDoctorAuthoritySnapshot(actor.accountId(), true, true, coreRole, actor.staffId(),
                        staffActive, department, true, AT));
    }

    private void coreWithoutStaff(ActorIdentity admin) {
        when(authority.currentDoctorAuthority(admin.accountId(), CORRELATION)).thenReturn(
                new CurrentDoctorAuthoritySnapshot(admin.accountId(), true, true, "ADMIN", null, false, null, false, AT));
    }

    @org.junit.jupiter.api.Test
    void abandonedOpenAppointmentIsNoLongerABasis() {
        ActorIdentity doctor = staff(DOCTOR_C, "DOCTOR");
        core(doctor, "DOCTOR", CARDIOLOGY, true);
        openAppointmentOn(DOCTOR_C, LocalDate.of(2026, 10, 10));

        assertDenied(policy.forPatient(doctor, PATIENT, AT, CORRELATION), RecordReadPolicy.PURPOSE_REQUIRED);
    }

    @org.junit.jupiter.api.Test
    void openAppointmentThirtyDaysOldStillCounts() {
        ActorIdentity doctor = staff(DOCTOR_C, "DOCTOR");
        core(doctor, "DOCTOR", CARDIOLOGY, true);
        openAppointmentOn(DOCTOR_C, LocalDate.of(2026, 10, 11));

        assertAllowed(policy.forPatient(doctor, PATIENT, AT, CORRELATION), "CARE_ASSIGNMENT");
    }

    private void openAppointmentOn(UUID doctor, LocalDate date) {
        patientAppointments.add(Appointment.restore(UUID.randomUUID(), PATIENT, doctor, CARDIOLOGY, date,
                LocalTime.of(9, 0), AppointmentStatus.PENDING, "visit", AT.minusSeconds(86400), null,
                (short) 0, null, null, null, null, null, null, null));
    }

    private void appointment(UUID doctor, UUID department, AppointmentStatus status, Instant completedAt) {
        patientAppointments.add(Appointment.restore(UUID.randomUUID(), PATIENT, doctor, department,
                LocalDate.of(2026, 11, 9), LocalTime.of(9, 0), status, "visit", AT.minusSeconds(86400), null,
                (short) 0, UUID.randomUUID(), AT.minusSeconds(3600), null, null, null, null, completedAt));
    }

    private void declaration(ActorIdentity actor, String purpose) {
        when(declarations.findActive(actor.accountId(), PATIENT, AT)).thenReturn(Optional.of(
                new RecordAccessDeclaration(UUID.randomUUID(), actor.accountId(), actor.staffId(), actor.role(),
                        PATIENT, purpose, null, AT.minusSeconds(60), AT.plusSeconds(60))));
    }

    private static MedicalRecord record(UUID doctor, UUID department) {
        return MedicalRecord.restore(UUID.randomUUID(), PATIENT, doctor, department, LocalDate.of(2026, 11, 1),
                "symptoms", UUID.randomUUID(), List.<Diagnosis>of(), AT.minusSeconds(86400), null);
    }

    private static void assertAllowed(RecordReadDecision decision, String basis) {
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.basis()).isEqualTo(basis);
        assertThat(decision.denialCode()).isNull();
    }

    private static void assertDenied(RecordReadDecision decision, String denialCode) {
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.basis()).isNull();
        assertThat(decision.denialCode()).isEqualTo(denialCode);
    }
}
