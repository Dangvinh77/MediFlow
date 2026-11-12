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
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant closedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String chiefComplaint,
        @JsonInclude(JsonInclude.Include.NON_NULL) String presentIllness,
        @JsonInclude(JsonInclude.Include.NON_NULL) String pastHistory,
        @JsonInclude(JsonInclude.Include.NON_NULL) String allergyNote,
        @JsonInclude(JsonInclude.Include.NON_NULL) String physicalExam,
        @JsonInclude(JsonInclude.Include.NON_NULL) String advice,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID responsibleDoctorId,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<EffectiveRecordCorrectionDTO> effectiveCorrections
) {
    public MedicalRecordDTO(UUID recordId, UUID patientId, UUID doctorId, UUID departmentId, LocalDate examinationDate,
            String symptoms, UUID appointmentId, List<DiagnosisDTO> diagnoses, Instant createdAt, Instant updatedAt,
            MedicalRecordStatus status, RecordDisposition disposition, String dispositionNote, Instant completedAt,
            UUID previousRecordId, RecordClosureReason closureReason, String closureNote, Instant closedAt,
            String chiefComplaint, String presentIllness, String pastHistory, String allergyNote, String physicalExam,
            String advice) {
        this(recordId, patientId, doctorId, departmentId, examinationDate, symptoms, appointmentId, diagnoses, createdAt,
                updatedAt, status, disposition, dispositionNote, completedAt, previousRecordId, closureReason, closureNote,
                closedAt, chiefComplaint, presentIllness, pastHistory, allergyNote, physicalExam, advice, null, List.of());
    }

    public MedicalRecordDTO(UUID recordId, UUID patientId, UUID doctorId, UUID departmentId, LocalDate examinationDate,
            String symptoms, UUID appointmentId, List<DiagnosisDTO> diagnoses, Instant createdAt, Instant updatedAt,
            MedicalRecordStatus status, RecordDisposition disposition, String dispositionNote, Instant completedAt,
            UUID previousRecordId, RecordClosureReason closureReason, String closureNote, Instant closedAt) {
        this(recordId, patientId, doctorId, departmentId, examinationDate, symptoms, appointmentId, diagnoses, createdAt,
                updatedAt, status, disposition, dispositionNote, completedAt, previousRecordId, closureReason, closureNote,
                closedAt, null, null, null, null, null, null, null, List.of());
    }

    public MedicalRecordDTO(UUID recordId, UUID patientId, UUID doctorId, UUID departmentId, LocalDate examinationDate,
            String symptoms, UUID appointmentId, List<DiagnosisDTO> diagnoses, Instant createdAt, Instant updatedAt,
            MedicalRecordStatus status, RecordDisposition disposition, String dispositionNote, Instant completedAt) {
        this(recordId, patientId, doctorId, departmentId, examinationDate, symptoms, appointmentId, diagnoses, createdAt,
                updatedAt, status, disposition, dispositionNote, completedAt,
                null, null, null, null, null, null, null, null, null, null, null, List.of());
    }

    public MedicalRecordDTO {
        diagnoses = List.copyOf(diagnoses);
        effectiveCorrections = effectiveCorrections == null ? List.of() : List.copyOf(effectiveCorrections);
    }

    public static MedicalRecordDTO withEffectiveCorrections(MedicalRecordDTO record,
            List<EffectiveRecordCorrectionDTO> corrections) {
        return new MedicalRecordDTO(record.recordId, record.patientId, record.doctorId, record.departmentId,
                record.examinationDate, record.symptoms, record.appointmentId, record.diagnoses, record.createdAt,
                record.updatedAt, record.status, record.disposition, record.dispositionNote, record.completedAt,
                record.previousRecordId, record.closureReason, record.closureNote, record.closedAt, record.chiefComplaint,
                record.presentIllness, record.pastHistory, record.allergyNote, record.physicalExam, record.advice,
                record.responsibleDoctorId, corrections);
    }
}
