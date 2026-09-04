package com.mediflow.organization.domain.exception;

/**
 * Được throw khi Staff có JobTitle = DOCTOR
 * nhưng không có license number.
 */
public class DoctorLicenseRequiredException
        extends DomainException {

    public DoctorLicenseRequiredException() {
        super("Doctor must have a license number");
    }
}
