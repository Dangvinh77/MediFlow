package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

import lombok.Getter;

/**
 * Yêu cầu thanh toán cho một tập khoản phí đã chọn trong cùng tài khoản (V2 ledger, additive —
 * backend-spec/care-finance-v2/06-billing.md §3, §5).
 */
@Getter
public class PaymentRequest {

    private final UUID paymentRequestId;
    private final UUID invoiceId;
    private final UUID accountId;
    private final PaymentRequestPurpose purpose;
    private PaymentRequestStatus status;
    private final BigDecimal requestedAmount;
    private final String currency;
    private final Instant expiresAt;
    private final UUID createdBy;
    private final Instant createdAt;
    private Instant completedAt;

    private PaymentRequest(UUID paymentRequestId, UUID invoiceId, UUID accountId,
                            PaymentRequestPurpose purpose, PaymentRequestStatus status,
                            BigDecimal requestedAmount, String currency, Instant expiresAt,
                            UUID createdBy, Instant createdAt, Instant completedAt) {
        this.paymentRequestId = paymentRequestId;
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.purpose = purpose;
        this.status = status;
        this.requestedAmount = requestedAmount;
        this.currency = currency;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    /** Mở một yêu cầu thanh toán mới — {@code BILLING_PAYMENT_REQUEST_INVALID} nếu số tiền âm. */
    public static PaymentRequest open(UUID invoiceId, UUID accountId, PaymentRequestPurpose purpose,
                                       BigDecimal requestedAmount, String currency, Instant expiresAt,
                                       UUID createdBy, Instant createdAt) {
        if (requestedAmount == null || requestedAmount.signum() < 0) {
            throw new BillingRuleException("BILLING_PAYMENT_REQUEST_INVALID",
                    "Số tiền yêu cầu thanh toán không được âm");
        }
        return new PaymentRequest(null, invoiceId, accountId, purpose, PaymentRequestStatus.PENDING,
                requestedAmount, currency, expiresAt, createdBy, createdAt, null);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc mở. */
    public static PaymentRequest restore(UUID paymentRequestId, UUID invoiceId, UUID accountId,
                                          PaymentRequestPurpose purpose, PaymentRequestStatus status,
                                          BigDecimal requestedAmount, String currency, Instant expiresAt,
                                          UUID createdBy, Instant createdAt, Instant completedAt) {
        return new PaymentRequest(paymentRequestId, invoiceId, accountId, purpose, status,
                requestedAmount, currency, expiresAt, createdBy, createdAt, completedAt);
    }

    /** Ghi nhận đã trả một phần — vẫn PENDING/PARTIALLY_PAID → PARTIALLY_PAID. */
    public void markPartiallyPaid() {
        transitionTo(PaymentRequestStatus.PARTIALLY_PAID);
    }

    /** Ghi nhận đã trả đủ — chỉ khi đã đạt đủ số tiền yêu cầu (do application service xác định). */
    public void markPaid(Instant at) {
        transitionTo(PaymentRequestStatus.PAID);
        this.completedAt = at;
    }

    /** Hết hạn khi chưa trả đủ. */
    public void expire() {
        transitionTo(PaymentRequestStatus.EXPIRED);
    }

    /** Hủy khi còn PENDING, chưa có khoản trả nào ghi nhận. */
    public void cancel() {
        transitionTo(PaymentRequestStatus.CANCELLED);
    }

    public boolean isSettled() {
        return status == PaymentRequestStatus.PAID;
    }

    private void transitionTo(PaymentRequestStatus next) {
        if (!isValidTransition(status, next)) {
            throw new BillingRuleException("BILLING_INVALID_PAYMENT_REQUEST_TRANSITION",
                    "Không thể chuyển trạng thái yêu cầu thanh toán từ " + status + " sang " + next);
        }
        this.status = next;
    }

    private static boolean isValidTransition(PaymentRequestStatus from, PaymentRequestStatus to) {
        return switch (from) {
            case PENDING -> to == PaymentRequestStatus.PARTIALLY_PAID
                    || to == PaymentRequestStatus.PAID
                    || to == PaymentRequestStatus.EXPIRED
                    || to == PaymentRequestStatus.CANCELLED;
            case PARTIALLY_PAID -> to == PaymentRequestStatus.PAID || to == PaymentRequestStatus.EXPIRED;
            case PAID, EXPIRED, CANCELLED -> false;
        };
    }
}
