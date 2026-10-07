package com.mediflow.billing.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.domain.model.Charge;
import com.mediflow.billing.domain.model.PaymentTransaction;

/** Out-port cho charge ca mổ (CONTRACT-SURGERY-BILLING-01). Lock and every write participate
 * in one local Billing transaction, giống {@code LedgerPaymentRepositoryPort}. */
public interface SurgeryChargeRepositoryPort {

    /** Mở tài khoản mới cho đợt điều trị nếu chưa có, khóa hàng (FOR UPDATE) rồi trả về. */
    BillingAccount findOrOpenAccount(UUID patientId, UUID departmentId, CareEpisodeType careEpisodeType,
                                      UUID careEpisodeId, String currency, Instant openedAt);

    Optional<BillingAccount> findAccountById(UUID accountId);

    Optional<Charge> findChargeBySource(String sourceType, UUID sourceId, String priceCode);

    /** Mọi charge (POSTED hoặc VOIDED) của một nguồn — dùng khi hủy ca mổ. */
    List<Charge> findChargesBySource(String sourceType, UUID sourceId);

    Charge saveCharge(Charge charge);

    /** Cập nhật số lượng/đơn giá/thành tiền/reconciledResultId (đối chiếu) hoặc status/voidReason (hủy). */
    void updateCharge(Charge charge);

    /** Các khoản thanh toán PAYMENT đã hoàn tất từng phân bổ vào charge này — dùng để tính hoàn tiền. */
    List<ChargeAllocation> findCompletedAllocations(UUID chargeId);

    boolean refundTransactionExists(String idempotencyKey);

    /** Ghi giao dịch REFUND bất biến + một dòng PAYMENT_ALLOCATION đảo chiều cho đúng charge gốc. */
    void saveRefund(PaymentTransaction refund, UUID chargeId);

    record ChargeAllocation(UUID transactionId, BigDecimal amount, UUID paymentRequestId,
                            String currency, String paymentMethod) { }
}
