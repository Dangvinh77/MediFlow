package com.mediflow.organization.domain.exception;

/**
 * Base exception cho tất cả lỗi phát sinh từ business rule của domain.
 *
 * Application layer có thể bắt DomainException để chuyển thành
 * response phù hợp cho API.
 *
 * Ví dụ:
 *
 *     DoctorLicenseRequiredException
 *         ↓
 *     DomainException
 *         ↓
 *     Application
 *         ↓
 *     HTTP 400
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
