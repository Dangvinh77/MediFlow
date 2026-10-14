package com.mediflow.clinical.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

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
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.AppointmentNotFoundException;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.exception.MedicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
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
    private final CorrelationIdProvider correlationIds;
    private final ClinicalStaffAuthority staffAuthority;
    private final boolean outpatientMvpEnabled;

    @Autowired
    public MedicalRecordApplicationService(AppointmentRepositoryPort appointments, MedicalRecordRepositoryPort records,
                                           PatientLookupPort patients, StaffLookupPort staff,
                                           ClinicalEventPublisherPort publisher, ClinicalDtoMapper mapper,
                                           CorrelationIdProvider correlationIds,
                                           CurrentClinicalActorPort actors, OutpatientMvpMode outpatientMvpMode) {
        this(appointments, records, patients, staff, publisher, mapper, correlationIds,
                actors, outpatientMvpMode.enabled());
    }

    public MedicalRecordApplicationService(AppointmentRepositoryPort appointments, MedicalRecordRepositoryPort records,
                                           PatientLookupPort patients, StaffLookupPort staff,
                                           ClinicalEventPublisherPort publisher, ClinicalDtoMapper mapper,
                                           CorrelationIdProvider correlationIds,
                                           CurrentClinicalActorPort actors, boolean outpatientMvpEnabled) {
        this.appointments = appointments;
        this.records = records;
        this.patients = patients;
        this.staff = staff;
        this.publisher = publisher;
        this.mapper = mapper;
        this.correlationIds = correlationIds;
        this.staffAuthority = new ClinicalStaffAuthority(actors, staff);
        this.outpatientMvpEnabled = outpatientMvpEnabled;
    }

    @Override
    @Transactional
    public MedicalRecordDTO create(CreateRecordRequest request) {
        if (outpatientMvpEnabled) {
            throw legacyRecordWriteDisabled();
        }
        requirePatient(request.patientId());
        requireDoctorDepartment(request.doctorId(), request.departmentId());

        Appointment appointment = null;
        if (request.appointmentId() != null) {
            appointment = appointments.findByIdForUpdate(request.appointmentId())
                    .orElseThrow(() -> new AppointmentNotFoundException(
                            "Appointment not found: " + request.appointmentId()));
            if (!appointment.getPatientId().equals(request.patientId())) {
                throw new InvalidClinicalDataException("RECORD_APPOINTMENT_PATIENT_MISMATCH",
                        "Appointment belongs to another patient");
            }
            if (records.findByAppointmentId(request.appointmentId()).isPresent()) {
                throw new InvalidClinicalDataException("RECORD_DUPLICATE_APPOINTMENT",
                        "Appointment already has a medical record");
            }
        }

        boolean markAppointmentArrived = false;
        if (appointment != null) {
            markAppointmentArrived = appointment.isPending();
            if (!markAppointmentArrived && appointment.getStatus() != AppointmentStatus.ARRIVED) {
                throw new InvalidClinicalDataException("RECORD_APPOINTMENT_INVALID_STATUS",
                        "Appointment must be pending or arrived to create a medical record");
            }
        }

        List<Diagnosis> diagnoses = request.diagnoses().stream().map(mapper::toDomain).toList();
        MedicalRecord record = MedicalRecord.create(request.patientId(), request.doctorId(), request.departmentId(),
                request.examinationDate(), request.symptoms(), request.appointmentId(), diagnoses);
        if (markAppointmentArrived) {
            appointment.markArrived();
            appointments.save(appointment);
        }
        MedicalRecord saved = records.save(record);
        String correlationId = correlationIds.currentOrCreate().toString();
        publisher.publishMedicalRecordCreated(MedicalRecordCreatedEvent.from(saved, correlationId));
        if (markAppointmentArrived) {
            publisher.publishAppointmentStatusChanged(
                    AppointmentStatusChangedEvent.from(appointment, saved.getRecordId(), correlationId));
        }
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public MedicalRecordDTO update(UUID id, UpdateRecordRequest request) {
        MedicalRecord record = locked(id);
        requireOutpatientMvpRecordAuthority(record);
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
        requireOutpatientMvpRecordAuthority(record);
        Diagnosis diagnosis = mapper.toDomain(request);
        record.addDiagnosis(diagnosis);
        records.save(record);
        publisher.publishDiagnosisAdded(
                DiagnosisAddedEvent.from(recordId, diagnosis, correlationIds.currentOrCreate().toString()));
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

    private void requireOutpatientMvpRecordAuthority(MedicalRecord record) {
        if (!outpatientMvpEnabled) {
            return;
        }
        if (record.getAppointmentId() == null) {
            throw legacyRecordWriteDisabled();
        }
        Appointment appointment = appointments.findByIdForUpdate(record.getAppointmentId())
                .orElseThrow(() -> new InvalidClinicalDataException("CLINICAL_V0_WRITE_DISABLED",
                        "MVP record writes require an active appointment examination"));
        boolean sameTarget = record.getPatientId().equals(appointment.getPatientId())
                && record.getDoctorId().equals(appointment.getDoctorId())
                && record.getDepartmentId().equals(appointment.getDepartmentId());
        if (!sameTarget || appointment.getCareContractVersion() != 1
                || appointment.getStatus() != AppointmentStatus.IN_EXAM) {
            throw legacyRecordWriteDisabled();
        }
        staffAuthority.requireAssignedDoctor(appointment.getDoctorId(), appointment.getDepartmentId());
    }

    private static InvalidClinicalDataException legacyRecordWriteDisabled() {
        return new InvalidClinicalDataException("CLINICAL_V0_WRITE_DISABLED",
                "Legacy medical-record writes are disabled outside an active outpatient examination");
    }
}
