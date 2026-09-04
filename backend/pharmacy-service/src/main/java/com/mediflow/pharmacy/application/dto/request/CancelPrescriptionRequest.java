package com.mediflow.pharmacy.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu HTTP dùng để hủy một đơn thuốc.
 *
 * <p>Danh tính người hủy không nằm trong payload mà được lấy từ JWT đã xác thực.</p>
 *
 * @param reason lý do hủy, bắt buộc và tối đa 500 ký tự
 */
public record CancelPrescriptionRequest(
        @NotBlank(message = "Lý do hủy đơn thuốc là bắt buộc")
        @Size(max = 500, message = "Lý do hủy không được vượt quá 500 ký tự")
        String reason
) {
}
