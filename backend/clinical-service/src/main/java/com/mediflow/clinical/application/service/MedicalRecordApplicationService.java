package com.mediflow.clinical.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.request.AddDiagnosisRequest;
import com.mediflow.clinical.application.dto.request.CreateRecordRequest;
import com.mediflow.clinical.application.dto.request.UpdateRecordRequest;
import com.mediflow.clinical.application.dto.response.DiagnosisDTO;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.event.AppointmentStatusChangedEvent;
import com.mediflow.clinical.application.event.DiagnosisAddedEvent;
import com.mediflow.clinical.application.event.MedicalRecordCreatedEvent;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.port.in.ManageRecordUseCase;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalEventPublisherPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.AppointmentNotFoundException;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.exception.MedicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;

@Service
public class MedicalRecordApplicationService implements ManageRecordUseCase {

    private final AppointmentRepositoryPort appointments;
    private final MedicalRecordRepositoryPort records;
    private final PatientLookupPort patients;
    private final StaffLookupPort staff;
    private final ClinicalEventPublisherPort publisher;
    private final ClinicalDtoMapper mapper;

    public MedicalRecordApplicationService(AppointmentRepositoryPort appointments, MedicalRecordRepositoryPort records,
                                           PatientLookupPort patients, StaffLookupPort staff,
                                           ClinicalEventPublisherPort publisher, ClinicalDtoMapper mapper) {
        this.appointments = appointments;
        this.records = records;
        this.patients = patients;
        this.staff = staff;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public MedicalRecordDTO create(CreateRecordRequest request) {
        requirePatient(request.patientId());
        requireDoctorDepartment(request.doctorId(), request.departmentId());

        Appointment appointment = null;
        if (request.appointmentId() != null) {
            if (records.findByAppointmentId(request.appointmentId()).isPresent()) {
                throw new InvalidClinicalDataException("RECORD_DUPLICATE_APPOINTMENT",
                        "Appointment already has a medical record");
            }
            appointment = appointments.findByIdForUpdate(request.appointmentId())
                    .orElseThrow(() -> new AppointmentNotFoundException(
                            "Appointment not found: " + request.appointmentId()));
            if (!appointment.getPatientId().equals(request.patientId())) {
                throw new InvalidClinicalDataException("RECORD_APPOINTMENT_PATIENT_MISMATCH",
                        "Appointment belongs to another patient");
            }
        }

        List<Diagnosis> diagnoses = request.diagnoses().stream().map(mapper::toDomain).toList();
        MedicalRecord record = MedicalRecord.create(request.patientId(), request.doctorId(), request.departmentId(),
                request.examinationDate(), request.symptoms(), request.appointmentId(), diagnoses);
        if (appointment != null) {
            appointment.markArrived();
            appointments.save(appointment);
        }
        MedicalRecord saved = records.save(record);
        String correlationId = UUID.randomUUID().toString();
        publisher.publishMedicalRecordCreated(MedicalRecordCreatedEvent.from(saved, correlationId));
        if (appointment != null) {
            publisher.publishAppointmentStatusChanged(
                    AppointmentStatusChangedEvent.from(appointment, saved.getRecordId(), correlationId));
        }
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public MedicalRecordDTO update(UUID id, UpdateRecordRequest request) {
        MedicalRecord record = locked(id);
        record.update(request.symptoms());
        return mapper.toDto(records.save(record));
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalRecordDTO getById(UUID id) {
        return mapper.toDto(records.findById(id)
                .orElseThrow(() -> new MedicalRecordNotFoundException("Medical record not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MedicalRecordDTO> byPatient(UUID patientId) {
        return records.findByPatient(patientId).stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public DiagnosisDTO addDiagnosis(UUID recordId, AddDiagnosisRequest request) {
        MedicalRecord record = locked(recordId);
        Diagnosis diagnosis = mapper.toDomain(request);
        record.addDiagnosis(diagnosis);
        records.save(record);
        publisher.publishDiagnosisAdded(
                DiagnosisAddedEvent.from(recordId, diagnosis, UUID.randomUUID().toString()));
        return mapper.toDto(diagnosis);
    }

    private MedicalRecord locked(UUID id) {
        return records.findByIdForUpdate(id)
                .orElseThrow(() -> new MedicalRecordNotFoundException("Medical record not found: " + id));
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
}
