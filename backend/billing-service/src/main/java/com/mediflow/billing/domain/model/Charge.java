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
    private BigDecimal quantity;
    private BigDecimal unitAmount;
    private BigDecimal grossAmount;
    private ChargeStatus status;
    private String voidReason;
    private UUID reconciledResultId;
    private final Instant incurredAt;
    private final Instant createdAt;

    private Charge(UUID chargeId, UUID accountId, UUID patientId, UUID departmentId, String sourceType,
                    UUID sourceId, String priceCode, String description, BigDecimal quantity,
                    BigDecimal unitAmount, BigDecimal grossAmount, ChargeStatus status,
                    String voidReason, UUID reconciledResultId, Instant incurredAt, Instant createdAt) {
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
        this.reconciledResultId = reconciledResultId;
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
                description, quantity, unitAmount, gross, ChargeStatus.POSTED, null, null, incurredAt, null);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc ghi phí. */
    public static Charge restore(UUID chargeId, UUID accountId, UUID patientId, UUID departmentId,
                                  String sourceType, UUID sourceId, String priceCode, String description,
                                  BigDecimal quantity, BigDecimal unitAmount, BigDecimal grossAmount,
                                  ChargeStatus status, String voidReason, UUID reconciledResultId,
                                  Instant incurredAt, Instant createdAt) {
        return new Charge(chargeId, accountId, patientId, departmentId, sourceType, sourceId, priceCode,
                description, quantity, unitAmount, grossAmount, status, voidReason, reconciledResultId,
                incurredAt, createdAt);
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

    /**
     * Đối chiếu charge theo kết quả mổ thực tế — {@code resultId} bất biến là khóa đối chiếu
     * (CONTRACT-SURGERY-BILLING-01 "Performed items and completion"). Lần đầu gọi: cập nhật số
     * lượng/đơn giá/thành tiền theo thực tế đã mổ. Gửi lại đúng {@code resultId} với cùng số liệu
     * là vô hại (idempotent, redelivery). Một {@code resultId} khác, hoặc cùng resultId nhưng số
     * liệu đổi, là xung đột hợp đồng — không được âm thầm sửa đè charge đã đối chiếu.
     */
    public void reconcilePerformed(UUID resultId, BigDecimal performedQuantity, BigDecimal performedUnitAmount) {
        if (status == ChargeStatus.VOIDED) {
            throw new BillingRuleException("BILLING_CHARGE_ALREADY_VOIDED",
                    "Không thể đối chiếu charge đã bị hủy");
        }
        if (resultId == null) {
            throw new BillingRuleException("BILLING_SURGERY_RESULT_ID_REQUIRED", "resultId là bắt buộc");
        }
        if (performedQuantity == null || performedQuantity.signum() <= 0) {
            throw new BillingRuleException("BILLING_CHARGE_INVALID_QUANTITY", "Số lượng thực tế phải lớn hơn 0");
        }
        if (performedUnitAmount == null || performedUnitAmount.signum() < 0) {
            throw new BillingRuleException("BILLING_CHARGE_INVALID_AMOUNT", "Đơn giá thực tế không được âm");
        }
        if (reconciledResultId != null) {
            boolean sameResult = reconciledResultId.equals(resultId);
            boolean sameAmounts = quantity.compareTo(performedQuantity) == 0
                    && unitAmount.compareTo(performedUnitAmount) == 0;
            if (sameResult && sameAmounts) {
                return;
            }
            throw new BillingRuleException("BILLING_SURGERY_RECONCILIATION_CONFLICT",
                    "Kết quả mổ xung đột với dữ liệu đã đối chiếu cho charge này");
        }
        this.quantity = performedQuantity;
        this.unitAmount = performedUnitAmount;
        this.grossAmount = performedQuantity.multiply(performedUnitAmount).setScale(2, RoundingMode.HALF_UP);
        this.reconciledResultId = resultId;
    }
}
