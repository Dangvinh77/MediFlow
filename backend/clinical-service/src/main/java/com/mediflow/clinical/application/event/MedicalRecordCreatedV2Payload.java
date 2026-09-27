package com.mediflow.clinical.application.event;

import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.clinical.domain.model.CareEpisodeType;

public record MedicalRecordCreatedV2Payload(
        UUID recordId,
        UUID appointmentId,
        UUID patientId,
        UUID doctorId,
        UUID departmentId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        String sourceType,
        UUID sourceId,
        String priceCode,
        LocalDate examinationDate
) {
}
