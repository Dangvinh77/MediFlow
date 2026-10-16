package com.mediflow.billing.domain.exception;

import java.util.UUID;
import com.mediflow.common.exception.ResourceNotFoundException;

public class LedgerPaymentRequestNotFoundException extends ResourceNotFoundException {
    public LedgerPaymentRequestNotFoundException(UUID requestId) {
        super("BILLING_PAYMENT_REQUEST_NOT_FOUND", "Payment request not found: " + requestId);
    }

    /** Discovery-by-source has no request id to report yet; carry the lookup context instead. */
    public LedgerPaymentRequestNotFoundException(String reason) {
        super("BILLING_PAYMENT_REQUEST_NOT_FOUND", reason);
    }
}
