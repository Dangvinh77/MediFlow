package com.mediflow.billing.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;
import java.util.UUID;

public final class ScheduledPaymentRequestNotReadyException extends ResourceNotFoundException {
    public ScheduledPaymentRequestNotReadyException(UUID registrationId) {
        super("PAYMENT_REQUEST_NOT_READY", "Payment request is not ready for registration " + registrationId);
    }
}
