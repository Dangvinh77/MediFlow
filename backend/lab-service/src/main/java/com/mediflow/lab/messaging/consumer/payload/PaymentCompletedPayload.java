package com.mediflow.lab.messaging.consumer.payload;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Lab projection of Billing's {@code payment.completed} event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCompletedPayload(
        UUID eventId,
        List<UUID> labTestIds
) {}
