package com.mediflow.clinical.application.event;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Collectors;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.Diagnosis;
/** Publish after commit. This payload contains no lab order; lab must not auto-order from a diagnosis. */
public record MedicalRecordCreatedEvent(
        UUID eventId, Instant occurredAt, String correlationId,
        UUID recordId, UUID patientId, UUID doctorId, UUID departmentId,
        String diagnosis, LocalDate examinationDate
) {
    public static final String ROUTING_KEY = "medicalrecord.created";

    public static MedicalRecordCreatedEvent from(MedicalRecord record, String correlationId) {
        return new MedicalRecordCreatedEvent(UUID.randomUUID(), Instant.now(), correlationId,
                record.getRecordId(), record.getPatientId(), record.getDoctorId(), record.getDepartmentId(),
                record.getDiagnoses().stream().map(Diagnosis::getDiagnosisName).collect(Collectors.joining("; ")),
                record.getExaminationDate());
    }
}
