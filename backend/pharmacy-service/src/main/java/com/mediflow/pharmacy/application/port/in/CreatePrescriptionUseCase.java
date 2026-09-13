package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;

import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;

/**
 * In-port — "kê đơn thuốc" (bước khởi đầu của saga). Bác sĩ ghi nhận ý định dùng thuốc:
 * chỉ là ghi nhận, KHÔNG trừ kho ở đây. Server tự chụp giá từ kho tại thời điểm kê đơn,
 * tự tạo phiếu xuất PENDING, và publish prescription.created để billing tạo hóa đơn.
 * Đây chỉ là hợp đồng — PharmacyApplicationService sẽ hiện thực.
 */
public interface CreatePrescriptionUseCase {

   /**
     * Tạo đơn bằng danh tính đã được driving adapter xác thực.
     *
     * <p>Đơn phải có ít nhất một dòng; giá được lấy từ kho;
     * không trừ tồn thực tế tại bước này.</p>
     *
     * @param command dữ liệu đơn và danh tính người thực hiện
     * @return đơn thuốc đã được tạo
     */
    PrescriptionDTO create(CreatePrescriptionCommand command);
}
