package com.mediflow.pharmacy.application.port.in;

import java.util.UUID;

import com.mediflow.pharmacy.application.dto.response.DispenseDTO;

/**
 * In-port — "xuất thuốc theo đơn". Là bước DUY NHẤT làm biến động tồn kho của cả hệ
 * thống, nên nó mang nhiều quy tắc nhất (hết hàng, hết hạn, tương tranh, bù trừ saga).
 *
 * <p>Bước này được gọi từ <b>hai driving adapter</b>:
 * <ul>
 *   <li>{@code PUT /prescriptions/{id}/dispense} — dược sĩ xuất tay;</li>
 *   <li>consumer của event {@code payment.completed} — hệ thống tự xuất sau khi bệnh nhân
 *       trả tiền (qua {@code ReactToPaymentUseCase}).</li>
 * </ul>
 * Cả hai cùng đi qua <b>một</b> use case này — không bao giờ viết logic xuất hai lần.
 *
 * <p>Đây chỉ là hợp đồng — {@code PharmacyApplicationService} sẽ hiện thực.
 */
public interface DispensePrescriptionUseCase {

    /**
     * Xuất thuốc cho một đơn đã có phiếu xuất.
     *
     * <p>Quy trình (BR-D trong spec): phiếu phải đang {@code PENDING} (BR-D9, chống xuất 2 lần);
     * khóa ghi từng dòng đã sắp xếp theo drugId (BR-D10, chống deadlock); mỗi dòng phải đủ hàng
     * (BR-D1) và chưa hết hạn (BR-D2); trừ kho đúng một lần (BR-D4). Thành công → phiếu
     * {@code DISPENSED} + publish {@code prescription.filled}; chạm ngưỡng → publish {@code stock.low}
     * (BR-D11). Thất bại → phiếu {@code FAILED} trong transaction riêng (BR-D12) + publish
     * {@code prescription.dispense.failed} để billing bù trừ (BR-D6).
     *
     * @param prescriptionId đơn cần xuất
     * @param dispensedBy    id nhân viên thực hiện (dược sĩ) — khi do consumer gọi thì là id hệ thống
     * @return phiếu xuất sau khi xử lý (status DISPENSED hoặc FAILED)
     */
    DispenseDTO dispense(UUID prescriptionId, UUID dispensedBy);
}
