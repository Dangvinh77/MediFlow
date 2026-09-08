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

    public static MedicalRecord create(UUID patientId, UUID doctorId, UUID departmentId, LocalDate date,
                                       String symptoms, UUID appointmentId, List<Diagnosis> diagnoses) {
        return restore(UUID.randomUUID(), patientId, doctorId, departmentId, date, symptoms, appointmentId,
                diagnoses, Instant.now(), null);
    }

    public static MedicalRecord restore(UUID id, UUID patientId, UUID doctorId, UUID departmentId,
                                        LocalDate date, String symptoms, UUID appointmentId,
                                        List<Diagnosis> diagnoses, Instant createdAt, Instant updatedAt) {
        if (patientId == null || doctorId == null || departmentId == null) {
            throw new InvalidClinicalDataException("RECORD_REF_REQUIRED", "Patient, doctor and department are required");
        }
        if (date == null) {
            throw new InvalidClinicalDataException("RECORD_DATE_REQUIRED", "Examination date is required");
        }
        // BR-R1: the aggregate owns its nonempty collection; callers cannot erase diagnoses.
        if (diagnoses == null || diagnoses.isEmpty() || diagnoses.stream().anyMatch(Objects::isNull)) {
            throw new InvalidClinicalDataException("RECORD_NO_DIAGNOSIS", "At least one non-null diagnosis is required");
        }
        return new MedicalRecord(Objects.requireNonNull(id), patientId, doctorId, departmentId, date,
                symptoms, appointmentId, new ArrayList<>(diagnoses), Objects.requireNonNull(createdAt), updatedAt);
    }

    public void addDiagnosis(Diagnosis diagnosis) {
        if (diagnosis == null) {
            throw new InvalidClinicalDataException("RECORD_NO_DIAGNOSIS", "Diagnosis is required");
        }
        diagnoses.add(diagnosis);
        updatedAt = Instant.now();
    }

    public void update(String symptoms) {
        this.symptoms = symptoms;
        updatedAt = Instant.now();
    }

    public List<Diagnosis> getDiagnoses() { return List.copyOf(diagnoses); }
}
