package com.mediflow.billing.application.dto.response;

import com.mediflow.billing.domain.model.PaymentClassification;
import com.mediflow.billing.domain.model.PaymentOperationStatus;
import com.mediflow.billing.domain.model.ScheduledPaymentOperation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** V2 wire receipt: monetary fields are decimal strings by contract. */
public record ScheduledPaymentResult(OperationDTO operation, LedgerPaymentV2DTO payment, UUID clearanceId) {
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isPreparing() { return operation.status() == PaymentOperationStatus.PREPARING; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isClosedNotPaid() { return operation.status() == PaymentOperationStatus.CLOSED_NOT_PAID; }
    public static ScheduledPaymentResult preparing(ScheduledPaymentOperation source) {
        return new ScheduledPaymentResult(operation(source), null, null);
    }

    public static ScheduledPaymentResult completed(ScheduledPaymentOperation source) {
        var payment = new LedgerPaymentV2DTO(source.transactionId(), source.accountId(), source.paymentRequestId(),
                PaymentClassification.SERVICE_PAYMENT, "COMPLETED", source.amount().toPlainString(),
                source.currency(), wireMethod(source.paymentMethod().name()), source.completedAt());
        return new ScheduledPaymentResult(operation(source), "ZERO_LIABILITY".equals(source.basis()) ? null : payment, source.clearanceId());
    }

    public static ScheduledPaymentResult closed(ScheduledPaymentOperation source) {
        return new ScheduledPaymentResult(operation(source), null, null);
    }

    /** Test/helper factory whose identifiers and timestamps remain explicit. */
    public static ScheduledPaymentResult completed(UUID operationId, UUID requestId, UUID registrationId,
            UUID reservationId, UUID transactionId, UUID clearanceId, BigDecimal amount, String currency,
            Instant createdAt, Instant completedAt) {
        var operation = new OperationDTO(operationId, requestId, registrationId, reservationId,
                PaymentOperationStatus.COMPLETED, transactionId, amount.toPlainString(), currency,
                createdAt, completedAt);
        var payment = new LedgerPaymentV2DTO(transactionId, null, requestId, PaymentClassification.SERVICE_PAYMENT,
                "COMPLETED", amount.toPlainString(), currency, "CASH", completedAt);
        return new ScheduledPaymentResult(operation, payment, clearanceId);
    }

    public static ScheduledPaymentResult closed(UUID operationId, UUID requestId, UUID registrationId,
            UUID reservationId, BigDecimal amount, String currency, Instant createdAt, Instant updatedAt) {
        return new ScheduledPaymentResult(new OperationDTO(operationId, requestId, registrationId, reservationId,
                PaymentOperationStatus.CLOSED_NOT_PAID, null, amount.toPlainString(), currency, createdAt, updatedAt),
                null, null);
    }

    private static OperationDTO operation(ScheduledPaymentOperation source) {
        return new OperationDTO(source.paymentOperationId(), source.paymentRequestId(), source.registrationId(),
                source.reservationId(), source.status(), source.transactionId(), source.amount().toPlainString(),
                source.currency(), source.createdAt(), source.updatedAt(), source.basis(), source.quoteId(), source.completedAt());
    }

    private static String wireMethod(String method) { return "TRANSFER".equals(method) ? "BANK_TRANSFER" : method; }

    public record OperationDTO(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId,
                               UUID reservationId, PaymentOperationStatus status, UUID transactionId,
                               String amount, String currency, Instant createdAt, Instant updatedAt, String basis, UUID quoteId, Instant completedAt) {
        public OperationDTO(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId, UUID reservationId,
                PaymentOperationStatus status, UUID transactionId, String amount, String currency, Instant createdAt, Instant updatedAt) {
            this(paymentOperationId, paymentRequestId, registrationId, reservationId, status, transactionId,
                    amount, currency, createdAt, updatedAt, "PATIENT_PAYMENT", null,
                    status == PaymentOperationStatus.COMPLETED ? updatedAt : null);
        }
    }

    public record LedgerPaymentV2DTO(UUID transactionId, UUID accountId, UUID paymentRequestId,
                                     PaymentClassification classification, String status, String amount,
                                     String currency, String paymentMethod, Instant completedAt) { }
}
