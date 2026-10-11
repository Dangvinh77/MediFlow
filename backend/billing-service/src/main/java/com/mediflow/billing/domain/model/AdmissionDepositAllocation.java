package com.mediflow.billing.domain.model;

import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Explicit original-deposit allocation. Allocation is NOT recognition before final settlement. */
public record AdmissionDepositAllocation(UUID originalTransactionId, List<Line> lines, BigDecimal amount) {
    public AdmissionDepositAllocation {
        if (originalTransactionId == null || lines == null || lines.isEmpty() || lines.size() > 1000
                || lines.stream().anyMatch(java.util.Objects::isNull)) throw invalid();
        lines = List.copyOf(lines);
        if (lines.stream().map(Line::chargeId).distinct().count() != lines.size()) throw invalid();
        amount = money(amount);
        if (lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(amount) != 0) throw invalid();
    }
    public record Line(UUID chargeId, BigDecimal amount) {
        public Line {
            if (chargeId == null) throw invalid();
            amount = money(amount);
        }
    }

    public static AdmissionDepositAllocation plan(AdmissionLedgerSnapshot ledger, UUID original,
            Map<UUID, BigDecimal> requested) {
        if (ledger == null || original == null || requested == null || requested.isEmpty() || requested.size() > 1000
                || !(ledger.accountStatus() == AccountStatus.CHARGE_CLOSED || ledger.accountStatus() == AccountStatus.SETTLEMENT_PENDING)) throw invalid();
        var totals = ledger.reconcile();
        // Insurance is account-wide today; never guess a per-charge insurance allocation.
        if (totals.insuranceAmount().signum() != 0) throw new BillingRuleException(
                "BILLING_DEPOSIT_INSURANCE_ALLOCATION_UNSUPPORTED", "Explicit charge-level insurance allocation is required");
        var payment = ledger.transactions().stream().filter(row -> row.id().equals(original)).findFirst().orElseThrow(AdmissionDepositAllocation::invalid);
        if (payment.type() != PaymentTransactionType.PAYMENT || payment.status() != PaymentTransactionStatus.COMPLETED
                || payment.classification() != PaymentClassification.ADMISSION_DEPOSIT) throw invalid();
        var transactionById = new java.util.HashMap<UUID, AdmissionLedgerSnapshot.TransactionLine>();
        ledger.transactions().forEach(transaction -> transactionById.put(transaction.id(), transaction));
        var netByCharge = new java.util.HashMap<UUID, BigDecimal>();
        BigDecimal available = payment.amount();
        for (var transaction : ledger.transactions()) {
            if (transaction.status() == PaymentTransactionStatus.COMPLETED && original.equals(transaction.originalId())) {
                available = available.subtract(transaction.amount());
            }
        }
        for (var allocation : ledger.allocations()) {
            var transaction = transactionById.get(allocation.transactionId());
            boolean receipt = transaction.type() == PaymentTransactionType.PAYMENT;
            netByCharge.merge(allocation.chargeId(), receipt ? allocation.amount() : allocation.amount().negate(), BigDecimal::add);
            if (original.equals(transaction.id())) {
                available = available.subtract(allocation.amount());
                if (requested.containsKey(allocation.chargeId())) throw new BillingRuleException(
                        "BILLING_DEPOSIT_ALLOCATION_ALREADY_RECORDED", "Append-only allocation already exists for this original/charge");
            } else if (original.equals(transaction.originalId())) {
                available = available.add(allocation.amount());
            }
        }
        var lines = new ArrayList<Line>();
        BigDecimal total = new BigDecimal("0.00");
        if (requested.keySet().stream().anyMatch(java.util.Objects::isNull)) throw invalid();
        for (var chargeId : requested.keySet().stream().sorted().toList()) {
            var charge = ledger.charges().stream().filter(row -> row.id().equals(chargeId)).findFirst().orElseThrow(AdmissionDepositAllocation::invalid);
            var amount = money(requested.get(chargeId));
            var unpaid = charge.grossAmount().subtract(netByCharge.getOrDefault(chargeId, BigDecimal.ZERO));
            if (charge.status() != ChargeStatus.POSTED || amount.compareTo(unpaid) > 0) throw invalid();
            lines.add(new Line(chargeId, amount)); total = total.add(amount);
        }
        total = money(total);
        if (total.compareTo(available) > 0 || total.compareTo(totals.balance()) > 0) throw invalid();
        return new AdmissionDepositAllocation(original, List.copyOf(lines), total);
    }

    private static BigDecimal money(BigDecimal value) {
        try {
            if (value == null || value.signum() <= 0) throw invalid();
            var exact = value.setScale(2, RoundingMode.UNNECESSARY);
            if (exact.precision() > 19) throw invalid();
            return exact;
        } catch (ArithmeticException invalidAmount) { throw invalid(); }
    }

    private static BillingRuleException invalid() {
        return new BillingRuleException("BILLING_DEPOSIT_ALLOCATION_INVALID", "Original deposit and exact unpaid charge allocation required");
    }
}
