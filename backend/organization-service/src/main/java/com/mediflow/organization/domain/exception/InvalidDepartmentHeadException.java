package com.mediflow.organization.domain.exception;

/** Thrown when a staff member cannot become a department head. */
public class InvalidDepartmentHeadException
        extends DomainException {

    public InvalidDepartmentHeadException(String message) {
        super(message);
    }
}
