package com.mediflow.billing.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;
import java.util.UUID;

public final class ScheduledPaymentOperationNotFoundException extends ResourceNotFoundException {
    public ScheduledPaymentOperationNotFoundException(UUID id) {
        super("PAYMENT_OPERATION_NOT_FOUND", "Payment operation not found: " + id);
    }
}
