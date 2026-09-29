package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

import lombok.Getter;

/**
 * Điều chỉnh bảo hiểm append-only trên tài khoản admission (V2 ledger, additive — backend-spec/
 * care-finance-v2/06-billing.md §3, §6). REVERSAL luôn trỏ về APPROVAL gốc — Billing không bao giờ
 * ghi đè quyết định bảo hiểm đã ghi nhận, chỉ nối thêm dòng đảo ngược.
 */
@Getter
public class InsuranceAdjustment {

    private final UUID adjustmentId;
    private final UUID accountId;
    private final UUID admissionId;
    private final String decisionReference;
    private final InsuranceAdjustmentType adjustmentType;
    private final UUID originalAdjustmentId;
    private final BigDecimal amount;
    private final String reason;
    private final Instant createdAt;

    private InsuranceAdjustment(UUID adjustmentId, UUID accountId, UUID admissionId,
                                 String decisionReference, InsuranceAdjustmentType adjustmentType,
                                 UUID originalAdjustmentId, BigDecimal amount, String reason,
                                 Instant createdAt) {
        this.adjustmentId = adjustmentId;
        this.accountId = accountId;
        this.admissionId = admissionId;
        this.decisionReference = decisionReference;
        this.adjustmentType = adjustmentType;
        this.originalAdjustmentId = originalAdjustmentId;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public static InsuranceAdjustment record(UUID accountId, UUID admissionId, String decisionReference,
                                              InsuranceAdjustmentType adjustmentType,
                                              UUID originalAdjustmentId, BigDecimal amount, String reason,
                                              Instant createdAt) {
        if (amount == null || amount.signum() < 0) {
            throw new BillingRuleException("BILLING_INSURANCE_ADJUSTMENT_INVALID_AMOUNT",
                    "Số tiền điều chỉnh bảo hiểm không được âm");
        }
        if (adjustmentType == InsuranceAdjustmentType.REVERSAL && originalAdjustmentId == null) {
            throw new BillingRuleException("BILLING_INSURANCE_REVERSAL_REQUIRES_ORIGINAL",
                    "Đảo điều chỉnh bảo hiểm phải trỏ về điều chỉnh gốc");
        }
        return new InsuranceAdjustment(null, accountId, admissionId, decisionReference, adjustmentType,
                originalAdjustmentId, amount, reason, createdAt);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc ghi. */
    public static InsuranceAdjustment restore(UUID adjustmentId, UUID accountId, UUID admissionId,
                                               String decisionReference, InsuranceAdjustmentType adjustmentType,
                                               UUID originalAdjustmentId, BigDecimal amount, String reason,
                                               Instant createdAt) {
        return new InsuranceAdjustment(adjustmentId, accountId, admissionId, decisionReference,
                adjustmentType, originalAdjustmentId, amount, reason, createdAt);
    }
}
