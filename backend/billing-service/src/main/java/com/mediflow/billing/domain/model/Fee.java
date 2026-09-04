package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

import lombok.Getter;

/**
 * Một khoản viện phí (EXAM/LAB/DRUG/SERVICE) phát sinh cho bệnh nhân, sau đó được gộp vào một hóa đơn.
 * Không tự tính {@code invoiceId} — việc đó do {@link Invoice} và tầng application điều phối.
 */
@Getter
public class Fee {

    private final UUID feeId;
    private final UUID patientId;
    private final UUID recordId;
    private final UUID departmentId;
    private final UUID sourceRefId;
    private final FeeType feeType;
    private final LocalDate incurredDate;
    private final BigDecimal amount;
    private boolean isPaid;
    private UUID invoiceId;
    private final Instant createdAt;
    private Instant updatedAt;

    private Fee(UUID feeId, UUID patientId, UUID recordId, UUID departmentId, UUID sourceRefId,
                FeeType feeType, LocalDate incurredDate, BigDecimal amount, boolean isPaid,
                UUID invoiceId, Instant createdAt, Instant updatedAt) {
        this.feeId = feeId;
        this.patientId = patientId;
        this.recordId = recordId;
        this.departmentId = departmentId;
        this.sourceRefId = sourceRefId;
        this.feeType = feeType;
        this.incurredDate = incurredDate;
        this.amount = amount;
        this.isPaid = isPaid;
        this.invoiceId = invoiceId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Fee create(UUID patientId, UUID recordId, UUID departmentId, UUID sourceRefId,
                              FeeType feeType, LocalDate incurredDate, BigDecimal amount) {
        // BR: mọi khoản phí đều phải thuộc về một khoa và có loại phí xác định
        if (departmentId == null || feeType == null) {
            throw new BillingRuleException("BILLING_DEPT_REQUIRED",
                    "Khoản phí phải có khoa và loại phí xác định");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BillingRuleException("BILLING_AMOUNT_NEGATIVE", "Số tiền của khoản phí không được âm");
        }
        return new Fee(null, patientId, recordId, departmentId, sourceRefId, feeType, incurredDate,
                amount, false, null, null, null);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc tạo. */
    public static Fee restore(UUID feeId, UUID patientId, UUID recordId, UUID departmentId, UUID sourceRefId,
                               FeeType feeType, LocalDate incurredDate, BigDecimal amount, boolean isPaid,
                               UUID invoiceId, Instant createdAt, Instant updatedAt) {
        return new Fee(feeId, patientId, recordId, departmentId, sourceRefId, feeType, incurredDate,
                amount, isPaid, invoiceId, createdAt, updatedAt);
    }

    /** Gắn khoản phí này vào một hóa đơn khi hóa đơn được lập. */
    public void assignToInvoice(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    /** Đánh dấu đã thanh toán — gọi khi hóa đơn chứa khoản phí này được {@code pay()} (BR-B3). */
    public void markPaid() {
        this.isPaid = true;
    }

    /** Bù trừ: đảo lại trạng thái đã trả và gỡ khỏi hóa đơn khi saga xuất thuốc thất bại (BR-B4). */
    public void refund() {
        this.isPaid = false;
        this.invoiceId = null;
    }

    public boolean isUnpaid() {
        return !isPaid;
    }
}
