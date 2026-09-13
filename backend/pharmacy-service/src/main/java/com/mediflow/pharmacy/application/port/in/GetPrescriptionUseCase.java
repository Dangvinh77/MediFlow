package com.mediflow.pharmacy.application.port.in;

import java.util.UUID;

import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;

/**
 * In-port tra cứu chi tiết một đơn thuốc.
 *
 * <p>Port chỉ trả DTO read-only; controller không được truy cập entity hoặc repository trực tiếp.
 */
public interface GetPrescriptionUseCase {

    /**
     * Đọc đơn thuốc cùng trạng thái phiếu xuất hiện tại.
     *
     * @param prescriptionId mã đơn thuốc cần đọc
     * @return DTO đơn thuốc, giữ nguyên giá snapshot lúc kê
     */
    PrescriptionDTO getPrescriptionById(UUID prescriptionId);
}
