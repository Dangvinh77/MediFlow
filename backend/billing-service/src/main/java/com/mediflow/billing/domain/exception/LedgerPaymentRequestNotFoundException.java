package com.mediflow.billing.domain.exception;

import java.util.UUID;
import com.mediflow.common.exception.ResourceNotFoundException;

public class LedgerPaymentRequestNotFoundException extends ResourceNotFoundException {
    public LedgerPaymentRequestNotFoundException(UUID requestId) {
        super("BILLING_PAYMENT_REQUEST_NOT_FOUND", "Payment request not found: " + requestId);
    }
}
