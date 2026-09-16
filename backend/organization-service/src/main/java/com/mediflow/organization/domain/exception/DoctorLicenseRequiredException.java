package com.mediflow.organization.domain.exception;

/** Thrown when a doctor does not have a license number. */
public class DoctorLicenseRequiredException
        extends DomainException {

    public DoctorLicenseRequiredException() {
        super("Doctor must have a license number");
    }
}
