package com.mediflow.billing.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Billing-owned read projection before the live Clinical reservation revision is joined. */
public record RegistrationPaymentSourceSnapshot(UUID paymentRequestId, UUID invoiceId, UUID accountId,
        UUID registrationId, UUID reservationId, UUID patientId, UUID departmentId, String sourceType,
        UUID sourceId, String careEpisodeType, UUID careEpisodeId, String requestedAmount, String paidAmount,
        String remainingAmount, String currency, String status, Instant expiresAt, Instant observedAt) { }
