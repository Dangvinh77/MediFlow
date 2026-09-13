package com.mediflow.pharmacy.domain.exception;

import com.mediflow.common.exception.ForbiddenOperationException;

/**
 * Báo hiệu bác sĩ đang cố tạo đơn với mã bác sĩ không thuộc
 * danh tính đã xác thực.
 */
public class PrescriptionCreationForbiddenException
        extends ForbiddenOperationException {

    public static final String CODE =
            "PRESCRIPTION_CREATION_FORBIDDEN";

    public PrescriptionCreationForbiddenException(String message) {
        super(CODE, message);
    }
}