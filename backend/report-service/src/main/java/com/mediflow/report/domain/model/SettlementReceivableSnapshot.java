package com.mediflow.report.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import com.mediflow.report.domain.exception.ReportRuleException;

/** Versioned account stock, not a cash flow or an earned-revenue allocation. */
public record SettlementReceivableSnapshot(UUID settlementId, int settlementVersion, UUID supersedesSettlementId,
        UUID accountId, UUID admissionId, UUID patientId, UUID departmentId, String currency,
        BigDecimal grossAmount, BigDecimal insuranceAmount, BigDecimal patientLiability,
        BigDecimal completedPayments, BigDecimal completedRefunds, BigDecimal completedReversals,
        BigDecimal netAllocatedPayments, BigDecimal balance, Outcome estimatedOutcome,
        Instant reconciledAt, boolean finalSettlement, String reportZone) {
    public SettlementReceivableSnapshot {
        if (settlementId == null || accountId == null || admissionId == null || patientId == null || departmentId == null
                || settlementVersion < 1 || ((settlementVersion == 1) != (supersedesSettlementId == null))
                || settlementId.equals(supersedesSettlementId) || currency == null || !currency.matches("[A-Z]{3}")
                || reconciledAt == null || reportZone == null || finalSettlement) throw invalid();
        ZoneId.of(reportZone);
        grossAmount = money(grossAmount, false); insuranceAmount = money(insuranceAmount, false);
        patientLiability = money(patientLiability, false); completedPayments = money(completedPayments, false);
        completedRefunds = money(completedRefunds, false); completedReversals = money(completedReversals, false);
        netAllocatedPayments = money(netAllocatedPayments, false); balance = money(balance, true);
        if (insuranceAmount.compareTo(grossAmount) > 0
                || patientLiability.compareTo(grossAmount.subtract(insuranceAmount)) != 0
                || completedRefunds.add(completedReversals).compareTo(completedPayments) > 0
                || netAllocatedPayments.compareTo(completedPayments.subtract(completedRefunds).subtract(completedReversals)) > 0
                || netAllocatedPayments.compareTo(grossAmount) > 0
                || balance.compareTo(patientLiability.subtract(netAllocatedPayments)) != 0
                || outcome(balance) != estimatedOutcome) throw invalid();
    }
    public BigDecimal receivable() { return balance.max(new BigDecimal("0.00")); }
    public void requireSameAccountContext(SettlementReceivableSnapshot other) {
        if (!accountId.equals(other.accountId) || !admissionId.equals(other.admissionId)
                || !patientId.equals(other.patientId) || !departmentId.equals(other.departmentId)
                || !currency.equals(other.currency) || !reportZone.equals(other.reportZone)) throw invalid();
    }
    public void requireSuccessor(SettlementReceivableSnapshot next) {
        requireSameAccountContext(next);
        if (next.settlementVersion != Math.addExact(settlementVersion, 1)
                || !settlementId.equals(next.supersedesSettlementId) || next.reconciledAt.isBefore(reconciledAt)) throw invalid();
    }
    public enum Outcome { ADDITIONAL_PAYMENT_REQUIRED, REFUND_DUE, PAID_IN_FULL }
    private static Outcome outcome(BigDecimal balance) {
        return balance.signum() > 0 ? Outcome.ADDITIONAL_PAYMENT_REQUIRED : balance.signum() < 0 ? Outcome.REFUND_DUE : Outcome.PAID_IN_FULL;
    }
    private static BigDecimal money(BigDecimal value, boolean signed) {
        if (value == null || (!signed && value.signum() < 0) || value.precision() > 100 || Math.abs((long) value.scale()) > 100) throw invalid();
        try {
            var exact = value.setScale(2, RoundingMode.UNNECESSARY);
            if (exact.precision() > 19) throw invalid();
            return exact;
        } catch (ArithmeticException error) { throw invalid(); }
    }
    private static ReportRuleException invalid() {
        return new ReportRuleException("REPORT_SETTLEMENT_CONFLICT", "Inconsistent or unsupported settlement snapshot");
    }
}
