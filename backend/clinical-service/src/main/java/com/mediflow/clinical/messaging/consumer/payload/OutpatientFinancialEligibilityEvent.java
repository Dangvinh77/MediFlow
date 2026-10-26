package com.mediflow.clinical.messaging.consumer.payload;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.application.event.OutpatientEligibilityFact;

/** Exact v1 Billing event envelope for a scheduled outpatient payment result. */
public record OutpatientFinancialEligibilityEvent(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String producer,
        OutpatientEligibilityFact payload) {
}
