package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;

import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.DiagnosisJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.MedicalRecordJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.AppointmentJpaRepository;
import com.mediflow.clinical.infrastructure.persistence.repository.MedicalRecordJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MedicalRecordPersistenceAdapter implements MedicalRecordRepositoryPort {
    private final MedicalRecordJpaRepository repository;
    private final AppointmentJpaRepository appointments;

    @Override public MedicalRecord save(MedicalRecord value) {
        try {
            return toDomain(repository.saveAndFlush(toEntity(value)));
        } catch (DataIntegrityViolationException ex) {
            if (hasConstraint(ex, "uq_medical_record_appointment")) {
                throw new InvalidClinicalDataException("RECORD_DUPLICATE_APPOINTMENT",
                        "Appointment already has a medical record");
            }
            throw ex;
        }
    }
    @Override public Optional<MedicalRecord> findById(UUID id) { return repository.findAggregateById(id).map(this::toDomain); }
    @Override public Optional<MedicalRecord> findByIdForUpdate(UUID id) { return repository.findByIdForUpdate(id).map(this::toDomain); }
    @Override public List<MedicalRecord> findByPatient(UUID patientId) {
        return repository.findByPatientIdOrderByExaminationDateDesc(patientId).stream().map(this::toDomain).toList();
    }
    @Override public Optional<MedicalRecord> findByAppointmentId(UUID appointmentId) {
        return repository.findByAppointmentAppointmentId(appointmentId).map(this::toDomain);
    }

    private MedicalRecord toDomain(MedicalRecordJpaEntity e) {
        List<Diagnosis> diagnoses = e.getDiagnoses().stream()
                .map(d -> Diagnosis.restore(d.getDiagnosisId(), d.getDiagnosisName(), d.getDescription(), d.getIcdCode()))
                .toList();
        UUID appointmentId = e.getAppointment() == null ? null : e.getAppointment().getAppointmentId();
        return MedicalRecord.restore(e.getRecordId(), e.getPatientId(), e.getDoctorId(), e.getDepartmentId(),
                e.getExaminationDate(), e.getSymptoms(), appointmentId, diagnoses, e.getCreatedAt(), e.getUpdatedAt());
    }
    private MedicalRecordJpaEntity toEntity(MedicalRecord r) {
        MedicalRecordJpaEntity entity = MedicalRecordJpaEntity.builder().recordId(r.getRecordId())
                .patientId(r.getPatientId()).doctorId(r.getDoctorId()).departmentId(r.getDepartmentId())
                .examinationDate(r.getExaminationDate()).symptoms(r.getSymptoms())
                .appointment(r.getAppointmentId() == null ? null : appointments.getReferenceById(r.getAppointmentId()))
                .createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt()).build();
        entity.setDiagnoses(new ArrayList<>(r.getDiagnoses().stream().map(d -> DiagnosisJpaEntity.builder()
                .diagnosisId(d.getDiagnosisId()).record(entity).diagnosisName(d.getDiagnosisName())
                .description(d.getDescription()).icdCode(d.getIcdCode()).build()).toList()));
        return entity;
    }

    private static boolean hasConstraint(Throwable error, String constraint) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
