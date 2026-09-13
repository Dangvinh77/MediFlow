package com.mediflow.organization.domain.exception;

import java.util.UUID;

public class StaffNotFoundException extends RuntimeException {

    public StaffNotFoundException(UUID staffId) {
        super("Staff not found: " + staffId);
    }
}