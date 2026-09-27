package com.mediflow.lab.messaging.consumer.payload;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Versioned Billing event envelope for purpose-scoped clearance. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinancialClearanceEnvelope(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String producer,
        FinancialClearancePayload payload
) {}
