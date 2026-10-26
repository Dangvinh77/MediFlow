package com.mediflow.organization.domain.exception;

public class OwnStaffContactAccessDeniedException extends RuntimeException {
    public OwnStaffContactAccessDeniedException() { super("Own staff identity is required"); }
}
