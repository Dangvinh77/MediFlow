package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

import lombok.Getter;

/**
 * Phân bổ một phần số tiền của {@link PaymentTransaction} cho một {@link Charge} cụ thể (V2 ledger,
 * additive — backend-spec/care-finance-v2/06-billing.md §3). Việc tổng phân bổ không vượt quá số
 * dư giao dịch/khoản phí là trách nhiệm application service (cần khóa hàng + đọc số dư đã lưu),
 * domain chỉ đảm bảo từng dòng phân bổ dương.
 */
@Getter
public class PaymentAllocation {

    private final UUID allocationId;
    private final UUID transactionId;
    private final UUID chargeId;
    private final BigDecimal amount;
    private final Instant createdAt;

    private PaymentAllocation(UUID allocationId, UUID transactionId, UUID chargeId, BigDecimal amount,
                               Instant createdAt) {
        this.allocationId = allocationId;
        this.transactionId = transactionId;
        this.chargeId = chargeId;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public static PaymentAllocation allocate(UUID transactionId, UUID chargeId, BigDecimal amount,
                                              Instant createdAt) {
        if (amount == null || amount.signum() <= 0) {
            throw new BillingRuleException("BILLING_ALLOCATION_INVALID_AMOUNT",
                    "Số tiền phân bổ phải lớn hơn 0");
        }
        return new PaymentAllocation(null, transactionId, chargeId, amount, createdAt);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc phân bổ. */
    public static PaymentAllocation restore(UUID allocationId, UUID transactionId, UUID chargeId,
                                             BigDecimal amount, Instant createdAt) {
        return new PaymentAllocation(allocationId, transactionId, chargeId, amount, createdAt);
    }
}
