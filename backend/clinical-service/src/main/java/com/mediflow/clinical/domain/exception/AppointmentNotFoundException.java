package com.mediflow.clinical.domain.exception;

import com.mediflow.common.exception.ResourceNotFoundException;

public class AppointmentNotFoundException extends ResourceNotFoundException {
    public AppointmentNotFoundException(String message) {
        super("APPOINTMENT_NOT_FOUND", message);
    }
}
