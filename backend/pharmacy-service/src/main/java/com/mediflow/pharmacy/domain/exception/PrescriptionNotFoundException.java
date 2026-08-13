package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

public class PrescriptionNotFoundException extends ResourceNotFoundException {
 public PrescriptionNotFoundException(String message) {
        super("PRESCRIPTION_NOT_FOUND", message);
    }
}
