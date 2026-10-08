package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.event.SurgeryCancelledEvent;
import com.mediflow.billing.application.event.SurgeryCaseCreatedEvent;
import com.mediflow.billing.application.event.SurgeryCompletedEvent;

/**
 * In-port do consumer sự kiện Surgery gọi vào — tạo/đối chiếu/hủy charge ca mổ
 * (CONTRACT-SURGERY-BILLING-01). {@code SurgeryChargeService} hiện thực.
 *
 * <p>Mọi handler idempotent theo {@code eventId} qua {@code ProcessedEventPort}, cộng thêm khóa
 * nghiệp vụ riêng cho từng loại: charge dedup theo {@code (sourceType, sourceId, priceCode)}
 * (ràng buộc DB {@code uq_charge_source}), đối chiếu theo {@code resultId} bất biến
 * ({@link com.mediflow.billing.domain.model.Charge#reconcilePerformed}), và hoàn tiền theo
 * {@code idempotencyKey} dẫn xuất từ {@code cancellationId}.
 */
public interface SurgeryChargeUseCase {

    /** {@code surgery.case.created} → tạo charge dự kiến (POSTED) cho từng dòng {@code plannedItems}. */
    void onSurgeryCaseCreated(SurgeryCaseCreatedEvent e);

    /** {@code surgery.completed} → đối chiếu charge theo {@code performedItems} thực tế đã mổ. */
    void onSurgeryCompleted(SurgeryCompletedEvent e);

    /** {@code surgery.cancelled} → hủy charge chưa phân bổ thanh toán, hoàn tiền phần đã thanh toán. */
    void onSurgeryCancelled(SurgeryCancelledEvent e);
}
