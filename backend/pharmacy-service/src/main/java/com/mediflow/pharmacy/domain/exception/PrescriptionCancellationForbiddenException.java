package com.mediflow.pharmacy.domain.exception;

/**
 * Báo hiệu người dùng đã xác thực nhưng không sở hữu quyền nghiệp vụ để hủy đơn thuốc.
 *
 * <p>Exception này được web adapter ánh xạ thành HTTP 403. Nó tách lỗi quyền sở hữu khỏi lỗi
 * chuyển trạng thái, vốn được trả về dưới dạng lỗi quy tắc nghiệp vụ 422.</p>
 */
public class PrescriptionCancellationForbiddenException extends RuntimeException {

    /** Mã lỗi ổn định trả cho client. */
    public static final String CODE = "PRESCRIPTION_CANCELLATION_FORBIDDEN";

    /**
     * Tạo lỗi từ chối hủy đơn.
     *
     * @param message thông điệp an toàn để trả về client
     */
    public PrescriptionCancellationForbiddenException(String message) {
        super(message);
    }
}
