package com.mediflow.clinical.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

public class ClinicalRecordNotFoundException extends ResourceNotFoundException {
    public ClinicalRecordNotFoundException(String message) {
        super("CLINICAL_RECORD_NOT_FOUND", message);
    }
}
