package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

import lombok.Getter;

/**
 * Một khoản phí ghi trên sổ của {@link BillingAccount} (V2 ledger, additive — backend-spec/
 * care-finance-v2/06-billing.md §3). Khử trùng theo {@code (sourceType, sourceId, priceCode)} là
 * ràng buộc DB ({@code uq_charge_source}) do application service/persistence adapter đảm nhiệm,
 * không lặp lại ở tầng domain.
 */
@Getter
public class Charge {

    private final UUID chargeId;
    private final UUID accountId;
    private final UUID patientId;
    private final UUID departmentId;
    private final String sourceType;
    private final UUID sourceId;
    private final String priceCode;
    private final String description;
    private final BigDecimal quantity;
    private final BigDecimal unitAmount;
    private final BigDecimal grossAmount;
    private ChargeStatus status;
    private String voidReason;
    private final Instant incurredAt;
    private final Instant createdAt;

    private Charge(UUID chargeId, UUID accountId, UUID patientId, UUID departmentId, String sourceType,
                    UUID sourceId, String priceCode, String description, BigDecimal quantity,
                    BigDecimal unitAmount, BigDecimal grossAmount, ChargeStatus status,
                    String voidReason, Instant incurredAt, Instant createdAt) {
        this.chargeId = chargeId;
        this.accountId = accountId;
        this.patientId = patientId;
        this.departmentId = departmentId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.priceCode = priceCode;
        this.description = description;
        this.quantity = quantity;
        this.unitAmount = unitAmount;
        this.grossAmount = grossAmount;
        this.status = status;
        this.voidReason = voidReason;
        this.incurredAt = incurredAt;
        this.createdAt = createdAt;
    }

    /** Ghi một khoản phí mới — {@code grossAmount = quantity * unitAmount} (BILLING_ACCOUNT §4). */
    public static Charge post(UUID accountId, UUID patientId, UUID departmentId, String sourceType,
                               UUID sourceId, String priceCode, String description,
                               BigDecimal quantity, BigDecimal unitAmount, Instant incurredAt) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BillingRuleException("BILLING_CHARGE_INVALID_QUANTITY",
                    "Số lượng khoản phí phải lớn hơn 0");
        }
        if (unitAmount == null || unitAmount.signum() < 0) {
            throw new BillingRuleException("BILLING_CHARGE_INVALID_AMOUNT",
                    "Đơn giá khoản phí không được âm");
        }
        BigDecimal gross = quantity.multiply(unitAmount).setScale(2, RoundingMode.HALF_UP);
        return new Charge(null, accountId, patientId, departmentId, sourceType, sourceId, priceCode,
                description, quantity, unitAmount, gross, ChargeStatus.POSTED, null, incurredAt, null);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc ghi phí. */
    public static Charge restore(UUID chargeId, UUID accountId, UUID patientId, UUID departmentId,
                                  String sourceType, UUID sourceId, String priceCode, String description,
                                  BigDecimal quantity, BigDecimal unitAmount, BigDecimal grossAmount,
                                  ChargeStatus status, String voidReason, Instant incurredAt,
                                  Instant createdAt) {
        return new Charge(chargeId, accountId, patientId, departmentId, sourceType, sourceId, priceCode,
                description, quantity, unitAmount, grossAmount, status, voidReason, incurredAt, createdAt);
    }

    /**
     * Hủy khoản phí — bắt buộc lý do (chỗ tự quyết: cột {@code void_reason} trong DDL cho phép
     * NULL, nhưng domain đòi hỏi ghi rõ lý do để tránh hủy phí không có căn cứ).
     */
    public void voidCharge(String reason) {
        if (status == ChargeStatus.VOIDED) {
            throw new BillingRuleException("BILLING_CHARGE_ALREADY_VOIDED", "Khoản phí đã bị hủy trước đó");
        }
        if (reason == null || reason.isBlank()) {
            throw new BillingRuleException("BILLING_CHARGE_VOID_REASON_REQUIRED",
                    "Hủy khoản phí phải kèm lý do");
        }
        this.status = ChargeStatus.VOIDED;
        this.voidReason = reason;
    }

    public boolean isPosted() {
        return status == ChargeStatus.POSTED;
    }
}
