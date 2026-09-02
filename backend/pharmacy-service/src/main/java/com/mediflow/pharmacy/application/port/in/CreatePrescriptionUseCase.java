package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;

/**
 * In-port — "kê đơn thuốc" (bước khởi đầu của saga). Bác sĩ ghi nhận ý định dùng thuốc:
 * chỉ là ghi nhận, KHÔNG trừ kho ở đây. Server tự chụp giá từ kho tại thời điểm kê đơn,
 * tự tạo phiếu xuất PENDING, và publish prescription.created để billing tạo hóa đơn.
 * Đây chỉ là hợp đồng — PharmacyApplicationService sẽ hiện thực.
 */
public interface CreatePrescriptionUseCase {

    /**
     * Kê đơn từ request. Đơn phải có ít nhất 1 dòng (PRESCRIPTION_EMPTY);
     * client không gửi giá (BR-D8); tổng tiền = tổng(giá × số lượng) (BR-D5);
     * tự tạo phiếu xuất PENDING (BR-D3); không trừ kho.
     */
    PrescriptionDTO create(CreatePrescriptionRequest rq);
}
