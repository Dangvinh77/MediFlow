package com.mediflow.clinical.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

public class ClinicalAppointmentNotFoundException extends ResourceNotFoundException {
    public ClinicalAppointmentNotFoundException(String message) {
        super("CLINICAL_APPOINTMENT_NOT_FOUND", message);
    }
}
