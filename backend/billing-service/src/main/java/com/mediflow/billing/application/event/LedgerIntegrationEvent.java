package com.mediflow.billing.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable producer bytes are stored by the outbox adapter, never rebuilt on replay. */
public record LedgerIntegrationEvent(UUID eventId, String eventType, int version, Instant occurredAt,
                                     String correlationId, String producer, Object payload) {
    /** Initial final-only immutable earned operation, separate from non-final reconciliation namespace. */
    public record FinalSettlementRecognitionPayload(UUID recognitionOperationId,int recognitionRevision,UUID supersedesOperationId,
            UUID settlementId,int settlementVersion,UUID supersedesSettlementId,UUID accountId,UUID admissionId,
            UUID patientId,UUID accountDepartmentId,String currency,BigDecimal grossAmount,BigDecimal insuranceAmount,
            BigDecimal patientLiability,BigDecimal completedPayments,BigDecimal completedRefunds,BigDecimal completedReversals,
            BigDecimal netAllocatedPayments,BigDecimal balance,String outcome,Instant completedAt,boolean finalSettlement,
            List<ChargeRecognition> charges,List<AllocationEvidence> allocations,List<DepositRelease> depositReleases) {
        public FinalSettlementRecognitionPayload {charges=List.copyOf(charges);allocations=List.copyOf(allocations);depositReleases=List.copyOf(depositReleases);}
        public record ChargeRecognition(UUID chargeId,UUID departmentId,String sourceType,UUID sourceId,
                UUID performedOperationId,String performedFingerprint,Instant performedAt,BigDecimal earnedAmount) { }
        public record AllocationEvidence(UUID allocationId,UUID transactionId,UUID originalTransactionId,UUID chargeId,
                String transactionType,String classification,BigDecimal amount) { }
        public record DepositRelease(UUID originalTransactionId,UUID receiptDepartmentId,BigDecimal amount) { }
    }
    /** Allocation only. The amount remains unrecognized until a separate completed final settlement. */
    public record DepositAllocatedPayload(UUID allocationOperationId, UUID accountId, UUID admissionId,
            UUID patientId, UUID originalTransactionId, String currency, BigDecimal allocatedAmount,
            List<DepositAllocationLine> allocations, Instant allocatedAt, boolean finalSettlement) {
        public record DepositAllocationLine(UUID allocationId, UUID chargeId, BigDecimal amount) { }
    }
    /** Non-final account stock, never permission for discharge or recognition of revenue. */
    public record SettlementReconciledPayload(UUID settlementId, int settlementVersion,
            UUID supersedesSettlementId, UUID accountId, UUID admissionId, UUID patientId,
            UUID departmentId, String currency, BigDecimal grossAmount, BigDecimal insuranceAmount,
            BigDecimal patientLiability, BigDecimal completedPayments, BigDecimal completedRefunds,
            BigDecimal completedReversals, BigDecimal netAllocatedPayments, BigDecimal balance,
            String estimatedOutcome, Instant reconciledAt, boolean finalSettlement) { }
    public record PrescriptionPaymentRequestPayload(UUID invoiceId, UUID paymentRequestId, UUID accountId,
            UUID patientId, UUID departmentId, String careEpisodeType, UUID careEpisodeId, String purpose,
            UUID prescriptionId, BigDecimal totalAmount, String currency, Instant createdAt, Instant expiresAt) { }
    public record SurgeryPaymentRequestPayload(UUID invoiceId, UUID paymentRequestId, UUID accountId,
            UUID patientId, UUID departmentId, String careEpisodeType, UUID careEpisodeId, String purpose,
            UUID surgeryCaseId, UUID admissionId, BigDecimal totalAmount, String currency,
            Instant createdAt, Instant expiresAt) { }
    public record PaymentCompletedPayload(UUID transactionId, UUID invoiceId, UUID paymentRequestId,
            UUID accountId, UUID patientId, UUID departmentId, String careEpisodeType, UUID careEpisodeId,
            String classification, BigDecimal totalAmount, String currency, String paymentMethod,
            Instant completedAt, UUID prescriptionId, List<UUID> labTestIds) { }
    public record ClearanceGrantedPayload(UUID clearanceId, UUID invoiceId, UUID accountId,
            UUID patientId, String careEpisodeType, UUID careEpisodeId, String purpose,
            UUID appointmentId, UUID recordId, List<UUID> labTestIds, UUID prescriptionId,
            UUID admissionId, UUID surgeryCaseId, BigDecimal amount, String currency,
            String paymentMethod, Instant expiresAt, boolean emergencyOverride) { }
    public record PaymentRefundedPayload(UUID refundTransactionId, UUID originalTransactionId, UUID accountId,
            UUID patientId, UUID departmentId, String careEpisodeType, UUID careEpisodeId,
            BigDecimal amount, String currency, String reason, Instant completedAt) { }
    public record SettlementCompletedPayload(UUID settlementId, UUID admissionId, UUID accountId,
            UUID patientId, UUID departmentId, BigDecimal grossAmount, BigDecimal insuranceAmount,
            BigDecimal patientLiability, BigDecimal completedPayments, BigDecimal completedRefunds,
            BigDecimal balance, String outcome, Instant completedAt) { }
}
