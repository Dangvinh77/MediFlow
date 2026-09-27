package com.mediflow.lab.application.event;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.lab.domain.model.CareEpisodeType;

/** Request-time charge fact for a V2 Lab test. */
public record LabRequestV2Payload(
        UUID labId,
        UUID patientId,
        UUID recordId,
        UUID departmentId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        UUID sourceOrderId,
        String sourceType,
        UUID sourceId,
        String priceCode,
        String labType,
        Instant requestedAt,
        UUID emergencyOverrideId
) {}
