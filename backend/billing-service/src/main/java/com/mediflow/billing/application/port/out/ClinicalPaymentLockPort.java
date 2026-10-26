package com.mediflow.billing.application.port.out;

import com.mediflow.billing.domain.model.ScheduledPaymentLease;
import com.mediflow.billing.domain.model.ScheduledPaymentOperation;
import java.time.Instant;
import java.util.UUID;

/** Synchronous Clinical authority for acquiring and resolving the reservation payment lease. */
public interface ClinicalPaymentLockPort {
    ScheduledPaymentLease acquire(ScheduledPaymentOperation operation);
    void resolve(PaymentResolution resolution);

    record PaymentResolution(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId,
            UUID reservationId, long fenceToken, String outcome, UUID transactionId,
            UUID clearanceId, Instant completedAt, String correlationId, String basis, UUID quoteId) {
    public PaymentResolution(UUID paymentOperationId, UUID paymentRequestId, UUID registrationId,
            UUID reservationId, long fenceToken, String outcome, UUID transactionId,
            UUID clearanceId, Instant completedAt, String correlationId) { this(paymentOperationId, paymentRequestId, registrationId, reservationId, fenceToken, outcome, transactionId, clearanceId, completedAt, correlationId, "PATIENT_PAYMENT", null); }
 }

    class PaymentAuthorityUnavailableException extends RuntimeException {
        public PaymentAuthorityUnavailableException(String message) { super(message); }
        public PaymentAuthorityUnavailableException(String message, Throwable cause) { super(message, cause); }
    }

    class PaymentAcquireRejectedException extends RuntimeException {
        private final String errorCode;
        public PaymentAcquireRejectedException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        public String errorCode() { return errorCode; }
    }
}
