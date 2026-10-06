package com.mediflow.billing.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable producer bytes are stored by the outbox adapter, never rebuilt on replay. */
public record LedgerIntegrationEvent(UUID eventId, String eventType, int version, Instant occurredAt,
                                     String correlationId, String producer, Object payload) {
    public record PaymentCompletedPayload(UUID transactionId, UUID invoiceId, UUID paymentRequestId,
            UUID accountId, UUID patientId, UUID departmentId, String careEpisodeType, UUID careEpisodeId,
            String classification, BigDecimal totalAmount, String currency, String paymentMethod,
            Instant completedAt, UUID prescriptionId, List<UUID> labTestIds) { }
    public record ClearanceGrantedPayload(UUID clearanceId, UUID invoiceId, UUID accountId,
            UUID patientId, String careEpisodeType, UUID careEpisodeId, String purpose,
            UUID appointmentId, UUID recordId, List<UUID> labTestIds, UUID prescriptionId,
            UUID admissionId, UUID surgeryCaseId, BigDecimal amount, String currency,
            String paymentMethod, Instant expiresAt, boolean emergencyOverride) { }
}
