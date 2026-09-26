package com.mediflow.pharmacy.application.port.in;

import java.util.UUID;

import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.domain.model.DispenseActor;

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
 * <p>Đây chỉ là hợp đồng — {@code DispenseApplicationService} sẽ hiện thực.
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
     * @param dispensedBy legacy staff UUID compatibility overload; automated calls use typed actors
     * @return phiếu xuất sau khi xử lý (status DISPENSED hoặc FAILED)
     */
    DispenseDTO dispense(UUID prescriptionId, UUID dispensedBy);

    /**
     * Xuất thuốc và giữ mã tương quan của event hoặc request xuyên suốt các event kết quả.
     *
     * @param prescriptionId đơn cần xuất
     * @param dispensedBy legacy staff UUID
     * @param correlationId mã tương quan, có thể {@code null} với job nội bộ
     * @return phiếu xuất sau khi xử lý
     */
    default DispenseDTO dispense(UUID prescriptionId, UUID dispensedBy, String correlationId) {
        return dispense(prescriptionId, dispensedBy);
    }

    /** Manual dispense preserving whether the verified identity is staff or an admin account.
     *
     * @param prescriptionId prescription identity
     * @param actor verified staff or account actor
     * @param correlationId request trace id
     * @return resulting dispense slip
     */
    DispenseDTO dispense(
            UUID prescriptionId,
            DispenseActor actor,
            String correlationId);

    /** Manual dispense from a verified HTTP identity; the application resolves its audit kind.
     *
     * @param prescriptionId prescription identity
     * @param actor signed account and optional staff identity
     * @param correlationId request trace id
     * @return resulting dispense slip
     */
    DispenseDTO dispense(
            UUID prescriptionId,
            ActorIdentity actor,
            String correlationId);

    /**
     * Dispenses from the trusted payment workflow after its receipt has been claimed.
     * Driving adapters must never expose this method directly to clients.
     *
     * @param prescriptionId prescription to dispense
     * @param actor explicit trusted actor; system automation has no user UUID
     * @param invoiceId related invoice
     * @param correlationId saga correlation id
     * @return resulting dispense slip
     */
    DispenseDTO dispenseWithPaymentProof(
            UUID prescriptionId,
            DispenseActor actor,
            UUID invoiceId,
            String correlationId);
}
