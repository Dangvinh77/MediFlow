package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

import lombok.Getter;

/**
 * Một giao dịch tiền bất biến trên {@link BillingAccount} (V2 ledger, additive — backend-spec/
 * care-finance-v2/06-billing.md §3, §5). REFUND/REVERSAL luôn phải trỏ về giao dịch gốc
 * ({@code ck_refund_original}) — không cho phép sửa giao dịch đã ghi, chỉ nối thêm giao dịch mới.
 */
@Getter
public class PaymentTransaction {

    private final UUID transactionId;
    private final UUID accountId;
    private final UUID paymentRequestId;
    private final PaymentTransactionType transactionType;
    private final PaymentClassification classification;
    private PaymentTransactionStatus status;
    private final BigDecimal amount;
    private final String currency;
    private final String paymentMethod;
    private final String providerReference;
    private final String idempotencyKey;
    private final UUID originalTransactionId;
    private Instant completedAt;
    private final Instant createdAt;

    private PaymentTransaction(UUID transactionId, UUID accountId, UUID paymentRequestId,
                                PaymentTransactionType transactionType, PaymentClassification classification,
                                PaymentTransactionStatus status, BigDecimal amount, String currency,
                                String paymentMethod, String providerReference, String idempotencyKey,
                                UUID originalTransactionId, Instant completedAt, Instant createdAt) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.paymentRequestId = paymentRequestId;
        this.transactionType = transactionType;
        this.classification = classification;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.providerReference = providerReference;
        this.idempotencyKey = idempotencyKey;
        this.originalTransactionId = originalTransactionId;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
    }

    /** Mở một giao dịch mới ở trạng thái PENDING. */
    public static PaymentTransaction open(UUID accountId, UUID paymentRequestId,
                                           PaymentTransactionType transactionType,
                                           PaymentClassification classification, BigDecimal amount,
                                           String currency, String paymentMethod,
                                           String providerReference, String idempotencyKey,
                                           UUID originalTransactionId, Instant createdAt) {
        if (amount == null || amount.signum() <= 0) {
            throw new BillingRuleException("BILLING_TRANSACTION_INVALID_AMOUNT",
                    "Số tiền giao dịch phải lớn hơn 0");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BillingRuleException("BILLING_IDEMPOTENCY_KEY_REQUIRED",
                    "Giao dịch phải có idempotency key");
        }
        if (transactionType != PaymentTransactionType.PAYMENT && originalTransactionId == null) {
            throw new BillingRuleException("BILLING_REFUND_REQUIRES_ORIGINAL",
                    "Giao dịch hoàn tiền/đảo giao dịch phải trỏ về giao dịch gốc");
        }
        return new PaymentTransaction(null, accountId, paymentRequestId, transactionType, classification,
                PaymentTransactionStatus.PENDING, amount, currency, paymentMethod, providerReference,
                idempotencyKey, originalTransactionId, null, createdAt);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc mở. */
    public static PaymentTransaction restore(UUID transactionId, UUID accountId, UUID paymentRequestId,
                                              PaymentTransactionType transactionType,
                                              PaymentClassification classification,
                                              PaymentTransactionStatus status, BigDecimal amount,
                                              String currency, String paymentMethod,
                                              String providerReference, String idempotencyKey,
                                              UUID originalTransactionId, Instant completedAt,
                                              Instant createdAt) {
        return new PaymentTransaction(transactionId, accountId, paymentRequestId, transactionType,
                classification, status, amount, currency, paymentMethod, providerReference,
                idempotencyKey, originalTransactionId, completedAt, createdAt);
    }

    /** Hoàn tất giao dịch (PENDING → COMPLETED). */
    public void complete(Instant at) {
        requirePending("BILLING_TRANSACTION_ALREADY_FINALIZED");
        this.status = PaymentTransactionStatus.COMPLETED;
        this.completedAt = at;
    }

    /** Đánh dấu thất bại (PENDING → FAILED). */
    public void fail() {
        requirePending("BILLING_TRANSACTION_ALREADY_FINALIZED");
        this.status = PaymentTransactionStatus.FAILED;
    }

    public boolean isCompleted() {
        return status == PaymentTransactionStatus.COMPLETED;
    }

    public boolean isRefundOrReversal() {
        return transactionType != PaymentTransactionType.PAYMENT;
    }

    private void requirePending(String errorCode) {
        if (status != PaymentTransactionStatus.PENDING) {
            throw new BillingRuleException(errorCode,
                    "Giao dịch không còn ở trạng thái PENDING (hiện tại: " + status + ")");
        }
    }
}
