package com.mediflow.clinical.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.request.CreateAppointmentRequest;
import com.mediflow.clinical.application.dto.request.UpdateAppointmentRequest;
import com.mediflow.clinical.application.dto.response.AppointmentDTO;
import com.mediflow.clinical.application.event.AppointmentCreatedEvent;
import com.mediflow.clinical.application.event.AppointmentStatusChangedEvent;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.port.in.ManageAppointmentUseCase;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalEventPublisherPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.AppointmentNotFoundException;
import com.mediflow.clinical.domain.exception.DuplicatePendingAppointmentException;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

@Service
public class AppointmentApplicationService implements ManageAppointmentUseCase {

    private final AppointmentRepositoryPort appointments;
    private final PatientLookupPort patients;
    private final StaffLookupPort staff;
    private final ClinicalEventPublisherPort publisher;
    private final ClinicalDtoMapper mapper;

    public AppointmentApplicationService(AppointmentRepositoryPort appointments, PatientLookupPort patients,
                                         StaffLookupPort staff, ClinicalEventPublisherPort publisher,
                                         ClinicalDtoMapper mapper) {
        this.appointments = appointments;
        this.patients = patients;
        this.staff = staff;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AppointmentDTO create(CreateAppointmentRequest request) {
        requirePatient(request.patientId());
        requireDoctorDepartment(request.doctorId(), request.departmentId());
        if (appointments.existsPendingSameDay(request.patientId(), request.appointmentDate())) {
            throw duplicatePending();
        }
        Appointment saved = appointments.save(Appointment.create(request.patientId(), request.doctorId(),
                request.departmentId(), request.appointmentDate(), request.appointmentTime(), request.reason()));
        publisher.publishAppointmentCreated(AppointmentCreatedEvent.from(saved, correlationId()));
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public AppointmentDTO update(UUID id, UpdateAppointmentRequest request) {
        Appointment appointment = locked(id);
        if (appointment.isPending() && appointments.existsPendingSameDayExcludingId(
                appointment.getPatientId(), request.appointmentDate(), id)) {
            throw duplicatePending();
        }
        appointment.update(request.appointmentDate(), request.appointmentTime(), request.reason());
        return mapper.toDto(appointments.save(appointment));
    }

    @Override
    @Transactional
    public AppointmentDTO changeStatus(UUID id, AppointmentStatus status) {
        Appointment appointment = locked(id);
        appointment.changeStatus(status);
        Appointment saved = appointments.save(appointment);
        publisher.publishAppointmentStatusChanged(AppointmentStatusChangedEvent.from(saved, null, correlationId()));
        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentDTO getById(UUID id) {
        return mapper.toDto(appointments.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException("Appointment not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentDTO> byPatient(UUID patientId) {
        return appointments.findByPatient(patientId).stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AppointmentDTO> search(UUID departmentId, LocalDate date, PageQuery page) {
        return appointments.search(departmentId, date, page).map(mapper::toDto);
    }

    private void requirePatient(UUID patientId) {
        if (!patients.exists(patientId)) {
            throw new InvalidClinicalDataException("PATIENT_NOT_FOUND_REMOTE", "Patient does not exist");
        }
    }

    private void requireDoctorDepartment(UUID doctorId, UUID departmentId) {
        UUID actual = staff.departmentOf(doctorId).orElseThrow(() -> new InvalidClinicalDataException(
                "DOCTOR_NOT_FOUND_REMOTE", "Doctor does not exist"));
        if (!actual.equals(departmentId)) {
            throw new InvalidClinicalDataException("DOCTOR_WRONG_DEPARTMENT", "Doctor does not belong to department");
        }
    }

    private Appointment locked(UUID id) {
        return appointments.findByIdForUpdate(id)
                .orElseThrow(() -> new AppointmentNotFoundException("Appointment not found: " + id));
    }

    private DuplicatePendingAppointmentException duplicatePending() {
        return new DuplicatePendingAppointmentException("Patient already has a pending appointment on this date");
    }

    private String correlationId() {
        return UUID.randomUUID().toString();
    }
}
