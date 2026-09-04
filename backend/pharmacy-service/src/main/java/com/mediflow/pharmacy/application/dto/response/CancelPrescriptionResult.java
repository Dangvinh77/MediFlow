package com.mediflow.pharmacy.application.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;

/**
 * Kết quả của thao tác hủy đơn thuốc.
 *
 * @param prescriptionId mã đơn đã xử lý
 * @param status trạng thái hiện tại của đơn
 * @param releasedReservations số dòng giữ chỗ vừa được giải phóng; bằng {@code 0} khi gọi
 *                              lặp lại một đơn đã hủy
 * @param cancelledAt thời điểm đơn được hủy lần đầu
 */
public record CancelPrescriptionResult(
        UUID prescriptionId,
        PrescriptionStatus status,
        int releasedReservations,
        Instant cancelledAt
) {
}
