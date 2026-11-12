package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.application.dto.request.CompleteRecordRequest;
import com.mediflow.clinical.application.dto.request.EmergencyOverrideRequest;
import com.mediflow.clinical.application.dto.request.StartExamRequest;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.port.out.AdmissionReferralRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.application.port.out.EmergencyOverrideRepositoryPort;
import com.mediflow.clinical.application.port.out.ExamClearanceRepositoryPort;
import com.mediflow.clinical.application.port.out.ExamPricePolicyPort;
import com.mediflow.clinical.application.port.out.FinancialExecutionAuthorityPort;
import com.mediflow.clinical.application.port.out.LabOrderLifecyclePort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.RecordDisposition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExamLifecycleRegressionTest {
    private static final StartExamRequest START = new StartExamRequest(null);

    private final IntakeHarness h = new IntakeHarness();
    private final StaffLookupPort staff = mock(StaffLookupPort.class);
    private final ClinicalDtoMapper mapper = mock(ClinicalDtoMapper.class);
    private ConsultationSession session;
    private OutpatientRegistration registration;
    private Appointment appointment;

    private ClinicalCareFinanceApplicationService service(Instant at) {
        Clock clock = Clock.fixed(at, ZoneOffset.UTC);
        return new ClinicalCareFinanceApplicationService(h.appointments, h.records, mock(ExamClearanceRepositoryPort.class),
                mock(FinancialExecutionAuthorityPort.class), mock(EmergencyOverrideRepositoryPort.class),
                mock(AdmissionReferralRepositoryPort.class), mock(ExamPricePolicyPort.class), h.outbox, h.actors, staff, mapper,
                h.correlationIds, clock, false, mock(ClinicalLabOrderRepositoryPort.class), mock(LabOrderLifecyclePort.class),
                h.registrationService(false));
    }

    @BeforeEach void setUp() {
        h.actAsDoctor();
        lenient().when(staff.departmentOf(h.doctorId)).thenReturn(Optional.of(h.departmentId));
        lenient().when(h.appointments.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(h.records.save(any())).thenAnswer(call -> call.getArgument(0));
        session = h.withCounts(h.session(), 0, 0, 1, 0);
        registration = h.stub(h.arrivedConfirmed(session));
        appointment = h.stubAppointment(registration, session);
    }

    private void ready() {
        appointment.confirmScheduledPayment(registration.reservation().clearanceId(), IntakeHarness.NOW);
    }

    private MedicalRecord started() {
        ready();
        registration = h.stub(registration.startExam(IntakeHarness.NOW));
        appointment.startScheduledExam(registration.reservation().clearanceId(), IntakeHarness.NOW);
        MedicalRecord record = MedicalRecord.openForAppointment(registration.patientId(), h.doctorId, h.departmentId, IntakeHarness.TODAY,
                null, appointment.getAppointmentId(), IntakeHarness.NOW);
        lenient().when(h.records.findByAppointmentId(appointment.getAppointmentId())).thenReturn(Optional.of(record));
        lenient().when(h.records.findByIdForUpdate(record.getRecordId())).thenReturn(Optional.of(record));
        return record;
    }

    @Test void examinationDateIsClinicDate() {
        ready();
        service(Instant.parse("2026-11-10T23:30:00Z")).startExam(appointment.getAppointmentId(), START);
        var saved = ArgumentCaptor.forClass(MedicalRecord.class);
        verify(h.records).save(saved.capture());
        assertThat(saved.getValue().getExaminationDate()).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    @Test void repeatStartReturnsSameRecord() {
        started();
        service(IntakeHarness.NOW).startExam(appointment.getAppointmentId(), START);
        verify(h.records, never()).save(any());
        verify(h.outbox, never()).append(any());
        verify(mapper).toDto(appointment);
    }

    @Test void nextDayContinuesSameRecord() {
        MedicalRecord record = started();
        service(IntakeHarness.NOW.plus(Duration.ofDays(1))).startExam(appointment.getAppointmentId(), START);
        verify(h.records, never()).save(any());
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.OPEN);
        assertThat(record.getExaminationDate()).isEqualTo(IntakeHarness.TODAY);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_EXAM);
    }

    @Test void nextDayCompletionKeepsOriginalDate() {
        MedicalRecord record = started();
        record.addDiagnosis(Diagnosis.create("Viêm họng", null, null));
        Instant nextDay = IntakeHarness.NOW.plus(Duration.ofDays(1));
        service(nextDay).complete(record.getRecordId(), new CompleteRecordRequest(RecordDisposition.OTHER, "Ổn định"));
        assertThat(record.getExaminationDate()).isEqualTo(IntakeHarness.TODAY);
        assertThat(record.getCompletedAt()).isEqualTo(nextDay);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test void shiftChangeCreatesNoNewRecord() {
        started();
        service(IntakeHarness.NOW.plus(Duration.ofHours(12))).startExam(appointment.getAppointmentId(), START);
        verify(h.records, never()).save(any());
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
    }

    @Test void failedCompletionLeavesAllThreeUnchanged() {
        MedicalRecord record = started();
        var complete = new CompleteRecordRequest(RecordDisposition.OTHER, "Thiếu chẩn đoán");
        assertThatThrownBy(() -> service(IntakeHarness.NOW).complete(record.getRecordId(), complete))
                .isInstanceOfSatisfying(InvalidClinicalDataException.class, e -> assertThat(e.getCode()).isEqualTo("CLINICAL_DIAGNOSIS_REQUIRED"));
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.OPEN);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_EXAM);
        assertThat(registration.status()).isEqualTo(OutpatientRegistrationStatus.IN_EXAM);
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
        verify(h.records, never()).save(any());
    }

    @Test void completedRecordCannotBeStartedAgain() {
        MedicalRecord record = started();
        record.addDiagnosis(Diagnosis.create("Viêm họng", null, null));
        service(IntakeHarness.NOW).complete(record.getRecordId(), new CompleteRecordRequest(RecordDisposition.OTHER, "Ổn định"));
        h.stub(registration.complete(IntakeHarness.NOW));
        org.mockito.Mockito.clearInvocations(h.records, h.outbox);
        assertThatThrownBy(() -> service(IntakeHarness.NOW).startExam(appointment.getAppointmentId(), START)).isInstanceOf(RuntimeException.class);
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.COMPLETED);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        verify(h.records, never()).save(any());
        verify(h.outbox, never()).append(any());
    }

    @Test void admissionDispositionRefusedWhenDeferred() {
        MedicalRecord record = started();
        record.addDiagnosis(Diagnosis.create("Viêm họng", null, null));
        var complete = new CompleteRecordRequest(RecordDisposition.ADMISSION, "Nhập viện");
        assertThatThrownBy(() -> service(IntakeHarness.NOW).complete(record.getRecordId(), complete))
                .isInstanceOfSatisfying(InvalidClinicalDataException.class, e -> assertThat(e.getCode()).isEqualTo("CLINICAL_ADMISSION_DEFERRED"));
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.OPEN);
    }

    @Test void scheduledStartNeedsClearance() {
        assertThatThrownBy(() -> service(IntakeHarness.NOW).startExam(appointment.getAppointmentId(), START)).isInstanceOf(RuntimeException.class);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.AWAITING_PAYMENT);
        verify(h.records, never()).save(any());
        verify(h.registrations, never()).update(any(), any(), any(), any(), any(), any());
    }

    @Test void scheduledStartRefusesEmergencyOverride() {
        ready();
        var override = new EmergencyOverrideRequest(UUID.randomUUID(), h.doctorId, "DOCTOR", "Cấp cứu", IntakeHarness.NOW);
        assertThatThrownBy(() -> service(IntakeHarness.NOW).startExam(appointment.getAppointmentId(), new StartExamRequest(override)))
                .isInstanceOfSatisfying(InvalidClinicalDataException.class, e -> assertThat(e.getCode()).isEqualTo("CLINICAL_REGISTRATION_FLOW_REQUIRED"));
        verify(h.records, never()).save(any());
    }
}
