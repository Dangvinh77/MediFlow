package com.mediflow.pharmacy.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.PaymentReceipt;

/**
 * Out-port lưu payment proof của pharmacy.
 *
 * <p>{@link #claim(PaymentReceipt)} phải claim theo thao tác atomic ở database, không triển khai
 * bằng chuỗi {@code exists → save}. Kết quả phải phân biệt receipt mới, duplicate cùng payload
 * và duplicate xung đột để consumer không ACK nhầm hoặc ghi đè outcome terminal. Port không biết
 * JPA/entity và chưa giả định business key cuối cùng của Billing; khi contract Billing chốt thêm
 * paymentId/attempt key, adapter và migration sẽ mở rộng cùng một task.</p>
 */
public interface PaymentReceiptRepositoryPort {

    /**
     * Claim một payment event ở trạng thái RECEIVED bằng unique eventId.
     *
     * @param receipt snapshot payload vừa nhận
     * @return kết quả claim có receipt hiện tại
     */
    PaymentReceiptClaimResult claim(PaymentReceipt receipt);

    /** @param eventId mã event từ Billing @return receipt theo event id */
    Optional<PaymentReceipt> findByEventId(UUID eventId);

    /**
     * Tìm tất cả receipt của một prescription; trả list vì chính sách nhiều payment attempt
     * vẫn chờ Billing xác nhận và pharmacy không được tự áp đặt one-to-one.
     */
    List<PaymentReceipt> findByPrescriptionId(UUID prescriptionId);

    /** Lưu transition outcome (DISPENSED/COMPENSATED) sau khi aggregate đã kiểm tra rule. */
    PaymentReceipt save(PaymentReceipt receipt);
}
