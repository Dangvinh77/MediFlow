package com.mediflow.clinical.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

public class MedicalRecordNotFoundException extends ResourceNotFoundException {
    public MedicalRecordNotFoundException(String message) {
        super("RECORD_NOT_FOUND", message);
    }
}
