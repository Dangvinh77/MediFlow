package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Durable, replayable binding between one cashier intent, one Billing request and one reservation. */
public record ScheduledPaymentOperation(
        UUID paymentOperationId,
        UUID paymentRequestId,
        UUID accountId,
        UUID registrationId,
        UUID reservationId,
        UUID patientId,
        String sourceType,
        UUID sourceId,
        UUID careEpisodeId,
        UUID actorAccountId,
        String idempotencyKey,
        String commandFingerprint,
        String correlationId,
        long expectedReservationRevision,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        String providerReference,
        PaymentOperationStatus status,
        UUID transactionId,
        UUID clearanceId,
        Long fenceToken,
        Long leaseReservationRevision,
        Instant lockedAt,
        Instant reconcileAfter,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        Instant resolutionAppliedAt, UUID quoteId, String basis) {
    public ScheduledPaymentOperation(
        UUID paymentOperationId,
        UUID paymentRequestId,
        UUID accountId,
        UUID registrationId,
        UUID reservationId,
        UUID patientId,
        String sourceType,
        UUID sourceId,
        UUID careEpisodeId,
        UUID actorAccountId,
        String idempotencyKey,
        String commandFingerprint,
        String correlationId,
        long expectedReservationRevision,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        String providerReference,
        PaymentOperationStatus status,
        UUID transactionId,
        UUID clearanceId,
        Long fenceToken,
        Long leaseReservationRevision,
        Instant lockedAt,
        Instant reconcileAfter,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        Instant resolutionAppliedAt) {
        this(paymentOperationId, paymentRequestId, accountId, registrationId, reservationId, patientId, sourceType, sourceId, careEpisodeId, actorAccountId, idempotencyKey, commandFingerprint, correlationId, expectedReservationRevision, amount, currency, paymentMethod, providerReference, status, transactionId, clearanceId, fenceToken, leaseReservationRevision, lockedAt, reconcileAfter, createdAt, updatedAt, completedAt, resolutionAppliedAt, null, "PATIENT_PAYMENT");
    }


    public boolean hasLease() { return fenceToken != null; }

    public ScheduledPaymentOperation withLease(ScheduledPaymentLease lease, Instant changedAt) {
        return new ScheduledPaymentOperation(paymentOperationId, paymentRequestId, accountId, registrationId, reservationId,
                patientId, sourceType, sourceId, careEpisodeId, actorAccountId, idempotencyKey, commandFingerprint,
                correlationId, expectedReservationRevision, amount, currency, paymentMethod, providerReference, status,
                transactionId, clearanceId, lease.fenceToken(), lease.reservationRevision(), lease.lockedAt(),
                lease.reconcileAfter(), createdAt, changedAt, completedAt, resolutionAppliedAt, quoteId, basis);
    }

    public ScheduledPaymentOperation completed(UUID paymentTransactionId, UUID financialClearanceId,
                                               Instant completed, Instant changedAt) {
        return new ScheduledPaymentOperation(paymentOperationId, paymentRequestId, accountId, registrationId, reservationId,
                patientId, sourceType, sourceId, careEpisodeId, actorAccountId, idempotencyKey, commandFingerprint,
                correlationId, expectedReservationRevision, amount, currency, paymentMethod, providerReference,
                PaymentOperationStatus.COMPLETED, paymentTransactionId, financialClearanceId, fenceToken,
                leaseReservationRevision, lockedAt, reconcileAfter, createdAt, changedAt, completed,
                resolutionAppliedAt, quoteId, basis);
    }

    public ScheduledPaymentOperation closed(String reason, Instant changedAt) {
        return new ScheduledPaymentOperation(paymentOperationId, paymentRequestId, accountId, registrationId, reservationId,
                patientId, sourceType, sourceId, careEpisodeId, actorAccountId, idempotencyKey, commandFingerprint,
                correlationId, expectedReservationRevision, amount, currency, paymentMethod, providerReference,
                PaymentOperationStatus.CLOSED_NOT_PAID, null, null, fenceToken, leaseReservationRevision,
                lockedAt, reconcileAfter, createdAt, changedAt, null, resolutionAppliedAt, quoteId, basis);
    }

    public ScheduledPaymentOperation resolutionApplied(Instant changedAt) {
        return new ScheduledPaymentOperation(paymentOperationId, paymentRequestId, accountId, registrationId, reservationId,
                patientId, sourceType, sourceId, careEpisodeId, actorAccountId, idempotencyKey, commandFingerprint,
                correlationId, expectedReservationRevision, amount, currency, paymentMethod, providerReference,
                status, transactionId, clearanceId, fenceToken, leaseReservationRevision, lockedAt, reconcileAfter,
                createdAt, changedAt, completedAt, changedAt, quoteId, basis);
    }
}
