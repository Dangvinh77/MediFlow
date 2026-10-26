package com.mediflow.organization.application.exception;

import org.springframework.security.access.AccessDeniedException;

public class SchedulingScopeDeniedException extends AccessDeniedException {
    public SchedulingScopeDeniedException() {
        super("Schedule-management scope is not available to this account");
    }
}
