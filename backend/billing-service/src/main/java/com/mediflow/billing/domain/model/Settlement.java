package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

import lombok.Getter;

/**
 * Một phiên bản quyết toán bất biến cho tài khoản admission (V2 ledger, additive — backend-spec/
 * care-finance-v2/06-billing.md §3, §6). Không sửa quyết toán cũ — mỗi lần tính lại tạo một phiên
 * bản mới nối tiếp qua {@code supersedesSettlementId}.
 *
 * <p>Chỗ tự quyết: DDL không ràng buộc dấu {@code balance} phải khớp {@code outcome} bằng CHECK,
 * nhưng §4 định nghĩa rõ công thức {@code balance = patientLiability - allocatedPayments} — domain
 * xác nhận outcome nhất quán với dấu của balance để lỗi tính toán bị chặn sớm nhất có thể, trừ
 * DEBT_APPROVED/WAIVED (ngoại lệ được duyệt, không bắt buộc balance = 0).
 */
@Getter
public class Settlement {

    private final UUID settlementId;
    private final UUID accountId;
    private final UUID admissionId;
    private final int settlementVersion;
    private final UUID supersedesSettlementId;
    private final BigDecimal grossAmount;
    private final BigDecimal insuranceAmount;
    private final BigDecimal patientLiability;
    private final BigDecimal completedPayments;
    private final BigDecimal completedRefunds;
    private final BigDecimal balance;
    private final SettlementOutcome outcome;
    private final Instant completedAt;

    private Settlement(UUID settlementId, UUID accountId, UUID admissionId, int settlementVersion,
                        UUID supersedesSettlementId, BigDecimal grossAmount, BigDecimal insuranceAmount,
                        BigDecimal patientLiability, BigDecimal completedPayments,
                        BigDecimal completedRefunds, BigDecimal balance, SettlementOutcome outcome,
                        Instant completedAt) {
        this.settlementId = settlementId;
        this.accountId = accountId;
        this.admissionId = admissionId;
        this.settlementVersion = settlementVersion;
        this.supersedesSettlementId = supersedesSettlementId;
        this.grossAmount = grossAmount;
        this.insuranceAmount = insuranceAmount;
        this.patientLiability = patientLiability;
        this.completedPayments = completedPayments;
        this.completedRefunds = completedRefunds;
        this.balance = balance;
        this.outcome = outcome;
        this.completedAt = completedAt;
    }

    public static Settlement create(UUID accountId, UUID admissionId, int settlementVersion,
                                     UUID supersedesSettlementId, BigDecimal grossAmount,
                                     BigDecimal insuranceAmount, BigDecimal patientLiability,
                                     BigDecimal completedPayments, BigDecimal completedRefunds,
                                     BigDecimal balance, SettlementOutcome outcome, Instant completedAt) {
        if (settlementVersion < 1) {
            throw new BillingRuleException("BILLING_SETTLEMENT_INVALID_VERSION",
                    "Phiên bản quyết toán phải bắt đầu từ 1");
        }
        requireOutcomeMatchesBalance(outcome, balance);
        return new Settlement(null, accountId, admissionId, settlementVersion, supersedesSettlementId,
                grossAmount, insuranceAmount, patientLiability, completedPayments, completedRefunds,
                balance, outcome, completedAt);
    }

    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc tạo. */
    public static Settlement restore(UUID settlementId, UUID accountId, UUID admissionId,
                                      int settlementVersion, UUID supersedesSettlementId,
                                      BigDecimal grossAmount, BigDecimal insuranceAmount,
                                      BigDecimal patientLiability, BigDecimal completedPayments,
                                      BigDecimal completedRefunds, BigDecimal balance,
                                      SettlementOutcome outcome, Instant completedAt) {
        return new Settlement(settlementId, accountId, admissionId, settlementVersion,
                supersedesSettlementId, grossAmount, insuranceAmount, patientLiability,
                completedPayments, completedRefunds, balance, outcome, completedAt);
    }

    private static void requireOutcomeMatchesBalance(SettlementOutcome outcome, BigDecimal balance) {
        if (balance == null) {
            throw new BillingRuleException("BILLING_SETTLEMENT_BALANCE_REQUIRED", "balance là bắt buộc");
        }
        boolean consistent = switch (outcome) {
            case PAID_IN_FULL -> balance.signum() == 0;
            case ADDITIONAL_PAYMENT_REQUIRED -> balance.signum() > 0;
            case REFUND_DUE -> balance.signum() < 0;
            case DEBT_APPROVED, WAIVED -> true;
        };
        if (!consistent) {
            throw new BillingRuleException("BILLING_SETTLEMENT_OUTCOME_MISMATCH",
                    "Outcome " + outcome + " không khớp dấu của balance");
        }
    }
}
