package com.mediflow.billing.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Out-port — "cho tôi biết loại của một xét nghiệm theo {@code labId}".
 *
 * <p>Vì sao cần: billing sinh phí {@code LAB} với số tiền {@code PriceListPort.labFee(labType)}
 * (06-billing.md §7), nhưng event {@code lab.result.created} <b>không</b> mang {@code labType}
 * (04-lab.md §8) — trường đó chỉ có ở {@code lab.request.created}.
 *
 * <p>Cách hiện thực (Phần 4-5/5, KHÔNG làm ở đây): adapter là một <b>bảng chiếu local</b>, billing
 * đăng ký thêm {@code lab.request.created} và lưu {@code (labId → labType)} vào bảng riêng, rồi
 * tra ở đây. <b>Tuyệt đối không</b> gọi REST đồng bộ sang lab-service từ trong consumer — làm vậy
 * biến luồng bất đồng bộ thành phụ thuộc đồng bộ (docs/ai/06-events-rabbitmq.md, docs/ai/01).
 * Cùng mẫu với "bảng chiếu email/phone" mà 07-notification.md §10 khuyến nghị.
 *
 * <p>Trả {@link Optional#empty()} khi chưa biết loại xét nghiệm — khi đó application <b>bỏ qua</b>
 * việc sinh phí (không đặt giá mặc định), đúng yêu cầu ở {@code THELOC-INTEGRATION-FOLLOWUP.md}.
 */
public interface LabTestTypePort {

    /** Loại xét nghiệm của {@code labId}, hoặc rỗng nếu bảng chiếu chưa có. */
    Optional<String> labType(UUID labId);
}
