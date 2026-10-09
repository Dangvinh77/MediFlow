package com.mediflow.notification.messaging.consumer.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Flat CURRENT Billing projection; fee details are deliberately not used in patient notices. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InvoiceCreatedPayload(UUID eventId, Instant occurredAt, String correlationId,
                                    UUID invoiceId, UUID patientId, BigDecimal totalAmount) {}
