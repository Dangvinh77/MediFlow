package com.mediflow.clinical.application.event;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.CareEpisodeType;

public record AppointmentStatusChangedV2Payload(
        UUID appointmentId,
        UUID recordId,
        UUID patientId,
        UUID departmentId,
        AppointmentStatus oldStatus,
        AppointmentStatus newStatus,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        String sourceType,
        UUID sourceId,
        String priceCode,
        Instant changedAt,
        UUID emergencyOverrideId
) {
}
