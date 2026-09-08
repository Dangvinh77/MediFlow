package com.mediflow.clinical.domain.exception;

import com.mediflow.common.exception.DuplicateResourceException;

public class DuplicatePendingAppointmentException extends DuplicateResourceException {
    public DuplicatePendingAppointmentException(String message) {
        super("APPOINTMENT_DUPLICATE_PENDING", message);
    }
}
