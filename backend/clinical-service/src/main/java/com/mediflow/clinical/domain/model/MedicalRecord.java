package com.mediflow.clinical.domain.model;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class MedicalRecord {
    private final UUID recordId;
    private final UUID patientId;
    private final UUID doctorId;
    private final UUID departmentId;
    private final LocalDate examinationDate;
    private String symptoms;
    private final UUID appointmentId;
    @Getter(AccessLevel.NONE)
    private final List<Diagnosis> diagnoses;
    private final Instant createdAt;
    private Instant updatedAt;
    private MedicalRecordStatus status;
    private RecordDisposition disposition;
    private String dispositionNote;
    private Instant completedAt;

    /** Legacy record creation retains the existing diagnosis-at-create rule. */
    public static MedicalRecord create(UUID patientId, UUID doctorId, UUID departmentId, LocalDate date,
                                       String symptoms, UUID appointmentId, List<Diagnosis> diagnoses) {
        validateDiagnosesForCompletion(diagnoses);
        return restore(UUID.randomUUID(), patientId, doctorId, departmentId, date, symptoms, appointmentId,
                diagnoses, Instant.now(), null);
    }

    /** V2 examination start opens an appointment record before diagnosis is entered. */
    public static MedicalRecord openForAppointment(UUID patientId, UUID doctorId, UUID departmentId,
                                                    LocalDate date, String symptoms, UUID appointmentId,
                                                    Instant createdAt) {
        if (appointmentId == null) {
            throw new InvalidClinicalDataException("CLINICAL_RECORD_NOT_FOUND",
                    "Appointment-backed record requires an appointment id");
        }
        return restore(UUID.randomUUID(), patientId, doctorId, departmentId, date, symptoms, appointmentId,
                List.of(), createdAt, null, MedicalRecordStatus.OPEN, null, null, null);
    }

    public static MedicalRecord restore(UUID id, UUID patientId, UUID doctorId, UUID departmentId,
                                        LocalDate date, String symptoms, UUID appointmentId,
                                        List<Diagnosis> diagnoses, Instant createdAt, Instant updatedAt) {
        return restore(id, patientId, doctorId, departmentId, date, symptoms, appointmentId, diagnoses,
                createdAt, updatedAt, MedicalRecordStatus.OPEN, null, null, null);
    }

    public static MedicalRecord restore(UUID id, UUID patientId, UUID doctorId, UUID departmentId,
                                        LocalDate date, String symptoms, UUID appointmentId,
                                        List<Diagnosis> diagnoses, Instant createdAt, Instant updatedAt,
                                        MedicalRecordStatus status, RecordDisposition disposition,
                                        String dispositionNote, Instant completedAt) {
        validateReferences(patientId, doctorId, departmentId, date);
        if (diagnoses == null || diagnoses.stream().anyMatch(Objects::isNull)) {
            throw new InvalidClinicalDataException("RECORD_NO_DIAGNOSIS",
                    "Diagnoses cannot contain null values");
        }
        if (status == null) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Medical record status is required");
        }
        if (status == MedicalRecordStatus.OPEN && (completedAt != null || disposition != null)) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Open record cannot have completion fields");
        }
        if (status == MedicalRecordStatus.COMPLETED
                && (completedAt == null || disposition == null || diagnoses.isEmpty())) {
            throw new InvalidClinicalDataException("CLINICAL_DISPOSITION_REQUIRED",
                    "Completed record requires a diagnosis, disposition and completion time");
        }
        return new MedicalRecord(Objects.requireNonNull(id), patientId, doctorId, departmentId, date,
                symptoms, appointmentId, new ArrayList<>(diagnoses), Objects.requireNonNull(createdAt),
                updatedAt, status, disposition, dispositionNote, completedAt);
    }

    public void addDiagnosis(Diagnosis diagnosis) {
        requireOpen();
        if (diagnosis == null) {
            throw new InvalidClinicalDataException("RECORD_NO_DIAGNOSIS", "Diagnosis is required");
        }
        diagnoses.add(diagnosis);
        updatedAt = Instant.now();
    }

    public void update(String symptoms) {
        requireOpen();
        this.symptoms = symptoms;
        updatedAt = Instant.now();
    }

    public void complete(RecordDisposition nextDisposition, String note, Instant at) {
        requireOpen();
        if (nextDisposition == null) {
            throw new InvalidClinicalDataException("CLINICAL_DISPOSITION_REQUIRED",
                    "Medical record completion requires a disposition");
        }
        if (diagnoses.isEmpty()) {
            throw new InvalidClinicalDataException("CLINICAL_DIAGNOSIS_REQUIRED",
                    "Medical record completion requires at least one diagnosis");
        }
        if (at == null) {
            throw new InvalidClinicalDataException("CLINICAL_DISPOSITION_REQUIRED",
                    "Medical record completion time is required");
        }
        status = MedicalRecordStatus.COMPLETED;
        disposition = nextDisposition;
        dispositionNote = note;
        completedAt = at;
        updatedAt = at;
    }

    public List<Diagnosis> getDiagnoses() {
        return List.copyOf(diagnoses);
    }

    private void requireOpen() {
        if (status != MedicalRecordStatus.OPEN) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Completed medical records are immutable");
        }
    }

    private static void validateReferences(UUID patientId, UUID doctorId, UUID departmentId, LocalDate date) {
        if (patientId == null || doctorId == null || departmentId == null) {
            throw new InvalidClinicalDataException("RECORD_REF_REQUIRED",
                    "Patient, doctor and department are required");
        }
        if (date == null) {
            throw new InvalidClinicalDataException("RECORD_DATE_REQUIRED", "Examination date is required");
        }
    }

    private static void validateDiagnosesForCompletion(List<Diagnosis> diagnoses) {
        if (diagnoses == null || diagnoses.isEmpty() || diagnoses.stream().anyMatch(Objects::isNull)) {
            throw new InvalidClinicalDataException("RECORD_NO_DIAGNOSIS",
                    "At least one non-null diagnosis is required");
        }
    }
}
