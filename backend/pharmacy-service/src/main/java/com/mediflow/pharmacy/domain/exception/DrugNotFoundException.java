package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;


public class DrugNotFoundException extends ResourceNotFoundException {
    public DrugNotFoundException(String message) {
        super("DRUG_NOT_FOUND", message);
    }
}