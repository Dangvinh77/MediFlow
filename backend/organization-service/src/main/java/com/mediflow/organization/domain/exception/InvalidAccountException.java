package com.mediflow.organization.domain.exception;

/**
 * Được throw khi Account vi phạm một business rule.
 *
 * Ví dụ:
 *
 * - PATIENT nhưng có staffId.
 * - Staff account nhưng không có staffId.
 * - Account inactive nhưng cố login.
 * - Password hash rỗng.
 */
public class InvalidAccountException
        extends DomainException {

    public InvalidAccountException(String message) {
        super(message);
    }
}
