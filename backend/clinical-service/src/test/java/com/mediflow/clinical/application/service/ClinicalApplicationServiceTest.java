package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import com.mediflow.clinical.application.dto.request.AddDiagnosisRequest;
import com.mediflow.clinical.application.dto.request.CreateAppointmentRequest;
import com.mediflow.clinical.application.dto.request.CreateRecordRequest;
import com.mediflow.clinical.application.event.AppointmentStatusChangedEvent;
import com.mediflow.clinical.application.event.DiagnosisAddedEvent;
import com.mediflow.clinical.application.event.MedicalRecordCreatedEvent;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalEventPublisherPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.DuplicatePendingAppointmentException;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;

class ClinicalApplicationServiceTest {

    private final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final PatientLookupPort patients = mock(PatientLookupPort.class);
    private final StaffLookupPort staff = mock(StaffLookupPort.class);
    private final ClinicalEventPublisherPort publisher = mock(ClinicalEventPublisherPort.class);
    private final ClinicalDtoMapper mapper = mock(ClinicalDtoMapper.class);
    private final AppointmentApplicationService appointmentService =
            new AppointmentApplicationService(appointments, patients, staff, publisher, mapper);
    private final MedicalRecordApplicationService recordService =
            new MedicalRecordApplicationService(appointments, records, patients, staff, publisher, mapper);

    @BeforeEach
    void mapDiagnosisRequests() {
        when(mapper.toDomain(any(AddDiagnosisRequest.class))).thenAnswer(invocation -> {
            AddDiagnosisRequest request = invocation.getArgument(0);
            return Diagnosis.create(request.diagnosisName(), request.description(), request.icdCode());
        });
    }

    @Test
    void createAppointment_secondPendingSameDay_throwsDuplicate() {
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(1);
        when(patients.exists(patient)).thenReturn(true);
        when(staff.departmentOf(doctor)).thenReturn(Optional.of(department));
        when(appointments.existsPendingSameDay(patient, date)).thenReturn(true);

        var request = new CreateAppointmentRequest(patient, doctor, department, date, LocalTime.of(9, 0), null);

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(DuplicatePendingAppointmentException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_DUPLICATE_PENDING");
        verify(appointments, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void createAppointment_doctorFromOtherDepartment_throwsBusinessRule() {
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        when(patients.exists(patient)).thenReturn(true);
        when(staff.departmentOf(doctor)).thenReturn(Optional.of(UUID.randomUUID()));

        var request = new CreateAppointmentRequest(patient, doctor, UUID.randomUUID(),
                LocalDate.now().plusDays(1), LocalTime.NOON, null);

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(InvalidClinicalDataException.class)
                .hasFieldOrPropertyWithValue("code", "DOCTOR_WRONG_DEPARTMENT");
        verifyNoInteractions(publisher);
    }

    @Test
    void createRecord_withAppointment_marksArrivedAtomicallyAndCorrelatesEvents() {
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        Appointment appointment = Appointment.create(patient, doctor, department,
                LocalDate.now().plusDays(1), LocalTime.NOON, null);
        when(patients.exists(patient)).thenReturn(true);
        when(staff.departmentOf(doctor)).thenReturn(Optional.of(department));
        when(records.findByAppointmentId(appointment.getAppointmentId())).thenReturn(Optional.empty());
        when(appointments.findByIdForUpdate(appointment.getAppointmentId())).thenReturn(Optional.of(appointment));
        when(appointments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(records.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateRecordRequest(patient, doctor, department, LocalDate.now(), "Fever",
                appointment.getAppointmentId(), List.of(new AddDiagnosisRequest("Influenza", null, "J10")));

        recordService.create(request);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.ARRIVED);
        ArgumentCaptor<MedicalRecord> saved = ArgumentCaptor.forClass(MedicalRecord.class);
        verify(records).save(saved.capture());
        verify(appointments).save(appointment);
        ArgumentCaptor<MedicalRecordCreatedEvent> recordEvent = ArgumentCaptor.forClass(MedicalRecordCreatedEvent.class);
        ArgumentCaptor<AppointmentStatusChangedEvent> statusEvent =
                ArgumentCaptor.forClass(AppointmentStatusChangedEvent.class);
        verify(publisher).publishMedicalRecordCreated(recordEvent.capture());
        verify(publisher).publishAppointmentStatusChanged(statusEvent.capture());
        assertThat(statusEvent.getValue().recordId()).isEqualTo(saved.getValue().getRecordId());
        assertThat(statusEvent.getValue().correlationId()).isEqualTo(recordEvent.getValue().correlationId());
    }

    @Test
    void createRecord_sameAppointmentTwice_rejectsDuplicateRecord() {
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        MedicalRecord existing = MedicalRecord.create(patient, doctor, department, LocalDate.now(), null,
                appointmentId, List.of(Diagnosis.create("Existing", null, null)));
        when(patients.exists(patient)).thenReturn(true);
        when(staff.departmentOf(doctor)).thenReturn(Optional.of(department));
        when(records.findByAppointmentId(appointmentId)).thenReturn(Optional.of(existing));

        var request = new CreateRecordRequest(patient, doctor, department, LocalDate.now(), null,
                appointmentId, List.of(new AddDiagnosisRequest("New", null, null)));

        assertThatThrownBy(() -> recordService.create(request))
                .isInstanceOf(InvalidClinicalDataException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_DUPLICATE_APPOINTMENT");
        verify(records, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void addDiagnosis_persistsAggregateAndPublishesEvent() {
        UUID recordId = UUID.randomUUID();
        MedicalRecord record = MedicalRecord.restore(recordId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), null, null, List.of(Diagnosis.create("Initial", null, null)),
                java.time.Instant.now(), null);
        when(records.findByIdForUpdate(recordId)).thenReturn(Optional.of(record));
        when(records.save(record)).thenReturn(record);

        recordService.addDiagnosis(recordId, new AddDiagnosisRequest("Hypertension", null, "I10"));

        assertThat(record.getDiagnoses()).hasSize(2);
        ArgumentCaptor<DiagnosisAddedEvent> event = ArgumentCaptor.forClass(DiagnosisAddedEvent.class);
        verify(publisher).publishDiagnosisAdded(event.capture());
        assertThat(event.getValue().recordId()).isEqualTo(recordId);
        assertThat(event.getValue().diagnosisCode()).isEqualTo("I10");
    }
}
