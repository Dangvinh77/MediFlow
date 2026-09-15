package com.mediflow.report.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.report.domain.exception.ReportRuleException;

/**
 * Invoice-keyed state machine that makes payment compensation reversible and idempotent.
 * The {@link TransitionEffect} tells the application layer which projection delta to apply.
 */
public final class PaymentContribution {

    public enum TransitionEffect { NONE, APPLY, REVERSE }

    private final UUID invoiceId;
    private UUID completedEventId;
    private UUID failedEventId;
    private LocalDate paymentDate;
    private UUID departmentId;
    private BigDecimal amount;
    private PaymentContributionStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private PaymentContribution(UUID invoiceId, UUID completedEventId, UUID failedEventId,
                                LocalDate paymentDate, UUID departmentId, BigDecimal amount,
                                PaymentContributionStatus status, Instant createdAt, Instant updatedAt) {
        this.invoiceId = invoiceId;
        this.completedEventId = completedEventId;
        this.failedEventId = failedEventId;
        this.paymentDate = paymentDate;
        this.departmentId = departmentId;
        this.amount = amount == null ? null : normalizeAmount(amount);
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Creates a transient/unpersisted contribution for an invoice not seen before. */
    public static PaymentContribution initialize(UUID invoiceId) {
        if (invoiceId == null) {
            throw new ReportRuleException("REPORT_INVOICE_ID_REQUIRED", "Mã hóa đơn là bắt buộc");
        }
        return new PaymentContribution(invoiceId, null, null, null, null, null,
                PaymentContributionStatus.NEW, null, null);
    }

    /** Rehydrates a contribution from persistence. */
    public static PaymentContribution restore(UUID invoiceId, UUID completedEventId,
                                              UUID failedEventId, LocalDate paymentDate,
                                              UUID departmentId, BigDecimal amount,
                                              PaymentContributionStatus status,
                                              Instant createdAt, Instant updatedAt) {
        if (invoiceId == null || status == null) {
            throw new ReportRuleException("REPORT_PERSISTED_DATA_INVALID",
                    "Contribution thanh toán đã lưu không hợp lệ");
        }
        if (status == PaymentContributionStatus.NEW) {
            throw new ReportRuleException("REPORT_PERSISTED_DATA_INVALID",
                    "Contribution đã lưu không được ở trạng thái NEW");
        }
        switch (status) {
            case PENDING_REVERSAL -> {
                if (failedEventId == null || completedEventId != null || paymentDate != null
                        || departmentId != null || amount != null) {
                    throw persistedStateInvalid();
                }
            }
            case APPLIED -> {
                if (failedEventId != null) {
                    throw persistedStateInvalid();
                }
                try {
                    validateCompletedData(completedEventId, paymentDate, amount);
                } catch (ReportRuleException ex) {
                    throw persistedStateInvalid();
                }
            }
            case REVERSED -> {
                if (failedEventId == null) {
                    throw persistedStateInvalid();
                }
                try {
                    validateCompletedData(completedEventId, paymentDate, amount);
                } catch (ReportRuleException ex) {
                    throw persistedStateInvalid();
                }
            }
            case NEW -> throw persistedStateInvalid();
        }
        return new PaymentContribution(invoiceId, completedEventId, failedEventId, paymentDate,
                departmentId, amount, status, createdAt, updatedAt);
    }

    private static ReportRuleException persistedStateInvalid() {
        return new ReportRuleException("REPORT_PERSISTED_DATA_INVALID",
                "Contribution thanh toán đã lưu không hợp lệ");
    }

    /** Applies a completed payment and returns the required projection effect. */
    public TransitionEffect complete(UUID eventId, LocalDate paymentDate,
                                     UUID departmentId, BigDecimal amount) {
        validateCompletedData(eventId, paymentDate, amount);
        BigDecimal normalized = normalizeAmount(amount);
        return switch (status) {
            case NEW -> {
                this.completedEventId = eventId;
                this.paymentDate = paymentDate;
                this.departmentId = departmentId;
                this.amount = normalized;
                this.status = PaymentContributionStatus.APPLIED;
                yield TransitionEffect.APPLY;
            }
            case PENDING_REVERSAL -> {
                this.completedEventId = eventId;
                this.paymentDate = paymentDate;
                this.departmentId = departmentId;
                this.amount = normalized;
                this.status = PaymentContributionStatus.REVERSED;
                yield TransitionEffect.NONE;
            }
            case APPLIED, REVERSED -> {
                ensureSameCompletedData(paymentDate, departmentId, normalized);
                yield TransitionEffect.NONE;
            }
        };
    }

    /** Applies a payment reversal and returns the required projection effect. */
    public TransitionEffect fail(UUID eventId) {
        if (eventId == null) {
            throw new ReportRuleException("REPORT_EVENT_ID_REQUIRED", "Mã event là bắt buộc");
        }
        return switch (status) {
            case NEW -> {
                failedEventId = eventId;
                status = PaymentContributionStatus.PENDING_REVERSAL;
                yield TransitionEffect.NONE;
            }
            case PENDING_REVERSAL -> TransitionEffect.NONE;
            case APPLIED -> {
                failedEventId = eventId;
                status = PaymentContributionStatus.REVERSED;
                yield TransitionEffect.REVERSE;
            }
            case REVERSED -> TransitionEffect.NONE;
        };
    }

    private void ensureSameCompletedData(LocalDate date, UUID department, BigDecimal normalizedAmount) {
        if (!paymentDate.equals(date) || !java.util.Objects.equals(departmentId, department)
                || amount.compareTo(normalizedAmount) != 0) {
            throw new ReportRuleException("REPORT_PAYMENT_CONFLICT",
                    "Các event thanh toán cùng hóa đơn có dữ liệu khác nhau");
        }
    }

    private static void validateCompletedData(UUID eventId, LocalDate date, BigDecimal amount) {
        if (eventId == null) {
            throw new ReportRuleException("REPORT_EVENT_ID_REQUIRED", "Mã event là bắt buộc");
        }
        if (date == null) {
            throw new ReportRuleException("REPORT_DATE_REQUIRED", "Ngày thanh toán là bắt buộc");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReportRuleException("REPORT_AMOUNT_INVALID", "Số tiền thanh toán phải lớn hơn 0");
        }
        normalizeAmount(amount);
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ReportRuleException("REPORT_AMOUNT_SCALE_INVALID",
                    "Số tiền thanh toán chỉ được có tối đa 2 chữ số thập phân");
        }
    }

    public UUID getInvoiceId() { return invoiceId; }
    public UUID getCompletedEventId() { return completedEventId; }
    public UUID getFailedEventId() { return failedEventId; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public UUID getDepartmentId() { return departmentId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentContributionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
