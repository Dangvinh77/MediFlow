package com.mediflow.surgery.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

import java.util.UUID;

/** Raised when a command references a Surgery case that does not exist. */
public final class SurgeryCaseNotFoundException extends ResourceNotFoundException {

    public SurgeryCaseNotFoundException(UUID surgeryCaseId) {
        super("SURGERY_CASE_NOT_FOUND", "Không tìm thấy ca phẫu thuật id=" + surgeryCaseId);
    }
}
