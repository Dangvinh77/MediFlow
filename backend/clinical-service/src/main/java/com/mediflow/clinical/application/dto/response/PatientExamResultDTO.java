package com.mediflow.clinical.application.dto.response;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.mediflow.clinical.domain.model.RecordDisposition;

/** Completed clinical summary released to its patient; no finance or staff command fields. */
public record PatientExamResultDTO(UUID recordId, UUID doctorId, UUID departmentId,
        LocalDate examinationDate, String symptoms, List<DiagnosisDTO> diagnoses,
        RecordDisposition disposition, String dispositionNote, Instant completedAt,
        List<EffectiveRecordCorrectionDTO> effectiveCorrections) {
    public PatientExamResultDTO {
        diagnoses = List.copyOf(diagnoses);
        effectiveCorrections = effectiveCorrections == null ? List.of() : List.copyOf(effectiveCorrections);
    }
    public PatientExamResultDTO(UUID recordId, UUID doctorId, UUID departmentId,
            LocalDate examinationDate, String symptoms, List<DiagnosisDTO> diagnoses,
            RecordDisposition disposition, String dispositionNote, Instant completedAt) {
        this(recordId, doctorId, departmentId, examinationDate, symptoms, diagnoses, disposition, dispositionNote,
                completedAt, List.of());
    }
    public static PatientExamResultDTO from(MedicalRecordDTO value) {
        return new PatientExamResultDTO(value.recordId(), value.doctorId(), value.departmentId(),
                value.examinationDate(), value.symptoms(), value.diagnoses(), value.disposition(),
                value.dispositionNote(), value.completedAt(), value.effectiveCorrections());
    }
}
