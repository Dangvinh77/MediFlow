package com.mediflow.clinical.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VitalSignObservationDTO(UUID observationId, UUID registrationId, Instant observedAt, UUID recordedByAccountId, UUID recordedByStaffId,
        Integer pulseBpm, Integer systolicMmhg, Integer diastolicMmhg, BigDecimal temperatureC, Integer respiratoryRate, Integer spo2Percent,
        BigDecimal weightKg, String notMeasuredReason) {}
