package com.mediflow.billing.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.billing.domain.model.Fee;
import com.mediflow.billing.domain.model.FeeType;

/**
 * Out-port — "tôi cần ai đó biết cách lưu và tìm khoản viện phí".
 * Application không tự đụng DB; nó khai lời hứa này, {@code FeePersistenceAdapter}
 * (trong infrastructure, làm ở Phần 4/5) sẽ hiện thực. Chữ ký bám sát
 * backend-spec/06-billing.md §6.
 */
public interface FeeRepositoryPort {

    /** Lưu mới hoặc cập nhật một khoản phí. Trả về đối tượng đã có đủ id + timestamps. */
    Fee save(Fee fee);

    /** Lưu nhiều khoản phí trong một lần (dùng khi thanh toán đánh dấu cả loạt phí, BR-B3). */
    List<Fee> saveAll(List<Fee> list);

    /** Tìm một khoản phí. Không có thì trả {@link Optional#empty()} → application ném {@code FeeNotFoundException}. */
    Optional<Fee> findById(UUID id);

    /** Các khoản phí chưa thanh toán của bệnh nhân — dùng để cộng {@code totalAmount} khi lập hóa đơn (BR-B2). */
    List<Fee> findUnpaidByPatient(UUID patientId);

    /** Các khoản phí đã gắn vào một hóa đơn — dùng khi thanh toán để {@code markPaid()} từng khoản. */
    List<Fee> findByInvoice(UUID invoiceId);

    /**
     * Đã tồn tại khoản phí sinh từ đúng event nguồn này chưa (BR-B7).
     * Là lớp phòng thủ trước khi tạo phí mới; unique partial index {@code uq_fee_source}
     * đảm bảo an toàn ngay cả khi có tương tranh.
     */
    boolean existsBySource(FeeType feeType, UUID sourceRefId);
}
