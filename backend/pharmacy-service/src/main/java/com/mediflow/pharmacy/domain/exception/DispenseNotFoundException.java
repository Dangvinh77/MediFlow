package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

public class DispenseNotFoundException extends ResourceNotFoundException {
     public DispenseNotFoundException(String message) {
        super("DISPENSE_NOT_FOUND", message);
    }
}
