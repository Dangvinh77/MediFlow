package com.mediflow.billing.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

public class InvoiceNotFoundException extends ResourceNotFoundException {
    public InvoiceNotFoundException(String message) {
        super("INVOICE_NOT_FOUND", message);
    }
}
