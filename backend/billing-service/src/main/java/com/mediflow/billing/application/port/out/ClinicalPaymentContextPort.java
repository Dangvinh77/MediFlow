package com.mediflow.billing.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Live, read-only reservation snapshot owned by Clinical. */
public interface ClinicalPaymentContextPort {
    PaymentContext get(UUID reservationId);

    record PaymentContext(UUID reservationId, UUID registrationId, UUID patientId,
                          int reservationRevision, String reservationStatus, Instant expiresAt) { }

    class PaymentContextUnavailableException extends RuntimeException {
        public PaymentContextUnavailableException(String message) { super(message); }
        public PaymentContextUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
