package com.mediflow.clinical.application.dto.response;

import java.util.UUID;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

public record MedicalRecordDTO(
        UUID recordId, UUID patientId, UUID doctorId, UUID departmentId,
        LocalDate examinationDate, String symptoms, UUID appointmentId,
        List<DiagnosisDTO> diagnoses, Instant createdAt, Instant updatedAt
) {
    public MedicalRecordDTO {
        diagnoses = List.copyOf(diagnoses);
    }
}
