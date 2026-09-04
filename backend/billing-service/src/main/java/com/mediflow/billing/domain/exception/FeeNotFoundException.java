package com.mediflow.billing.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

public class FeeNotFoundException extends ResourceNotFoundException {
    public FeeNotFoundException(String message) {
        super("FEE_NOT_FOUND", message);
    }
}
