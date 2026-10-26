package com.mediflow.billing.application.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Exact V2 cashier snapshot; reservationRevision is read live from Clinical on every lookup. */
public record RegistrationPaymentRequestDTO(UUID paymentRequestId, UUID invoiceId, UUID accountId,
        UUID registrationId, UUID reservationId, int reservationRevision, UUID patientId, UUID departmentId,
        String sourceType, UUID sourceId, String careEpisodeType, UUID careEpisodeId,
        String requestedAmount, String paidAmount, String remainingAmount, String currency,
        String status, Instant expiresAt, Instant observedAt) { }
