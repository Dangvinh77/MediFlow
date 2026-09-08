package com.mediflow.lab.domain.exception;

import java.util.UUID;

import com.mediflow.common.exception.ResourceNotFoundException;

/** Raised when a lab test cannot be found by its identifier. */
public class LabTestNotFoundException extends ResourceNotFoundException {

    public LabTestNotFoundException(String message) {
        super("LAB_NOT_FOUND", message);
    }

    public LabTestNotFoundException(UUID testId) {
        this("Không tìm thấy xét nghiệm: " + testId);
    }
}
