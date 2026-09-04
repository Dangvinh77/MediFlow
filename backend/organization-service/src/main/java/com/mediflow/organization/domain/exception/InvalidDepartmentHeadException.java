package com.mediflow.organization.domain.exception;

/**
 * Được throw khi Staff không đủ điều kiện trở thành trưởng khoa.
 *
 * Ví dụ:
 *
 * - Staff không active.
 * - Staff không thuộc Department.
 * - Staff không phải Doctor.
 */
public class InvalidDepartmentHeadException
        extends DomainException {

    public InvalidDepartmentHeadException(String message) {
        super(message);
    }
}
