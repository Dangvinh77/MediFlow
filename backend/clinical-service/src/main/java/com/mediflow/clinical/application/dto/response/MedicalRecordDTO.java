package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordDisposition;

public record MedicalRecordDTO(
        UUID recordId,
        UUID patientId,
        UUID doctorId,
        UUID departmentId,
        LocalDate examinationDate,
        String symptoms,
        UUID appointmentId,
        List<DiagnosisDTO> diagnoses,
        Instant createdAt,
        Instant updatedAt,
        MedicalRecordStatus status,
        RecordDisposition disposition,
        String dispositionNote,
        Instant completedAt
) {
    public MedicalRecordDTO {
        diagnoses = List.copyOf(diagnoses);
    }
}
