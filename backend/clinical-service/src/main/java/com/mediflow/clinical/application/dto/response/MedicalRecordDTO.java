package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordClosureReason;
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
        Instant completedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID previousRecordId,
        @JsonInclude(JsonInclude.Include.NON_NULL) RecordClosureReason closureReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String closureNote,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant closedAt
) {
    public MedicalRecordDTO(UUID recordId, UUID patientId, UUID doctorId, UUID departmentId, LocalDate examinationDate,
            String symptoms, UUID appointmentId, List<DiagnosisDTO> diagnoses, Instant createdAt, Instant updatedAt,
            MedicalRecordStatus status, RecordDisposition disposition, String dispositionNote, Instant completedAt) {
        this(recordId, patientId, doctorId, departmentId, examinationDate, symptoms, appointmentId, diagnoses, createdAt,
                updatedAt, status, disposition, dispositionNote, completedAt, null, null, null, null);
    }

    public MedicalRecordDTO {
        diagnoses = List.copyOf(diagnoses);
    }
}
