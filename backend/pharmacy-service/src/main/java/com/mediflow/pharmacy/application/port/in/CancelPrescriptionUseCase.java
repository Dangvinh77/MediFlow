package com.mediflow.pharmacy.application.port.in;

import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;

/**
 * In-port cho nghiệp vụ hủy đơn thuốc và trả lại phần tồn kho đang được giữ.
 *
 * <p>Use case chỉ cho phép hủy đơn còn hoạt động và có phiếu xuất đang chờ. Tất cả reservation
 * của đơn được chuyển từ {@code RESERVED} sang {@code RELEASED} trong cùng transaction.</p>
 */
public interface CancelPrescriptionUseCase {

    /**
     * Hủy một đơn thuốc theo danh tính đã được xác thực.
     *
     * @param command lệnh hủy chứa đơn, người thực hiện, quyền và lý do
     * @return trạng thái đơn cùng số reservation vừa được giải phóng
     */
    CancelPrescriptionResult cancel(CancelPrescriptionCommand command);
}
