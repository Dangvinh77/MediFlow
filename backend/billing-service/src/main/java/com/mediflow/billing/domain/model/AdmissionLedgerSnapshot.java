package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mediflow.billing.domain.exception.BillingRuleException;

/** Persisted ledger evidence, not a settlement command or permission to recognize revenue. */
public record AdmissionLedgerSnapshot(UUID accountId, UUID admissionId, UUID patientId,
        UUID departmentId, String currency, AccountStatus accountStatus, Instant chargeClosedAt,
        Instant observedAt, List<ChargeLine> charges, List<TransactionLine> transactions,
        List<AllocationLine> allocations, List<InsuranceLine> insurance) {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public AdmissionLedgerSnapshot {
        if (accountId == null || admissionId == null || patientId == null || departmentId == null
                || currency == null || !currency.matches("[A-Z]{3}") || accountStatus == null
                || observedAt == null || charges == null || transactions == null
                || allocations == null || insurance == null) throw invalid();
        if (charges.stream().anyMatch(java.util.Objects::isNull) || transactions.stream().anyMatch(java.util.Objects::isNull)
                || allocations.stream().anyMatch(java.util.Objects::isNull) || insurance.stream().anyMatch(java.util.Objects::isNull)) throw invalid();
        charges = List.copyOf(charges);
        transactions = List.copyOf(transactions);
        allocations = List.copyOf(allocations);
        insurance = List.copyOf(insurance);
    }

    public record ChargeLine(UUID id, UUID accountId, UUID patientId, ChargeStatus status, BigDecimal grossAmount) {
        public ChargeLine(UUID id, UUID accountId, UUID patientId, String status, BigDecimal grossAmount) {
            this(id, accountId, patientId, enumValue(ChargeStatus.class, status), grossAmount);
        }
    }
    public record TransactionLine(UUID id, UUID accountId, UUID originalId, PaymentTransactionType type, PaymentClassification classification,
            PaymentTransactionStatus status, BigDecimal amount, String currency, Instant completedAt) {
        public TransactionLine(UUID id, UUID accountId, UUID originalId, String type, String classification,
                String status, BigDecimal amount, String currency, Instant completedAt) {
            this(id, accountId, originalId, enumValue(PaymentTransactionType.class, type), enumValue(PaymentClassification.class, classification),
                    enumValue(PaymentTransactionStatus.class, status), amount, currency, completedAt);
        }
    }
    public record AllocationLine(UUID transactionId, UUID chargeId, BigDecimal amount) { }
    public record InsuranceLine(UUID id, UUID accountId, UUID admissionId, UUID originalId,
            InsuranceAdjustmentType type, BigDecimal amount) {
        public InsuranceLine(UUID id, UUID accountId, UUID admissionId, UUID originalId, String type, BigDecimal amount) {
            this(id, accountId, admissionId, originalId, enumValue(InsuranceAdjustmentType.class, type), amount);
        }
    }
    public record Totals(BigDecimal grossAmount, BigDecimal insuranceAmount, BigDecimal patientLiability,
            BigDecimal completedPayments, BigDecimal completedRefunds, BigDecimal completedReversals,
            BigDecimal netAllocatedPayments, BigDecimal unappliedNetPayments,
            BigDecimal unallocatedDeposit, BigDecimal balance, SettlementOutcome estimatedOutcome) { }

    /** Spec §4: allocation balance is not net cash or earned revenue. */
    public Totals reconcile() {
        if (accountStatus == AccountStatus.OPEN || chargeClosedAt == null || chargeClosedAt.isAfter(observedAt))
            throw new BillingRuleException("BILLING_SETTLEMENT_NOT_READY", "Admission charges are not closed");
        var chargeById = checkedCharges();
        var transactionById = checkedTransactions();
        var returnedByOriginal = checkedReturns(transactionById);
        var byTransaction = checkedAllocations(transactionById, chargeById);
        var allocationTotals = reconcileAllocations(transactionById, chargeById, byTransaction, returnedByOriginal);
        var gross = charges.stream().filter(charge -> charge.status() == ChargeStatus.POSTED)
                .map(ChargeLine::grossAmount).reduce(ZERO, BigDecimal::add);
        var insuranceAmount = approvedInsuranceAmount();
        var payments = completedAmount(PaymentTransactionType.PAYMENT);
        var refunds = completedAmount(PaymentTransactionType.REFUND);
        var reversals = completedAmount(PaymentTransactionType.REVERSAL);
        var unapplied = payments.subtract(refunds).subtract(reversals).subtract(allocationTotals.netAllocated());
        if (insuranceAmount.signum() < 0 || insuranceAmount.compareTo(gross) > 0
                || unapplied.signum() < 0 || allocationTotals.unallocatedDeposit().signum() < 0) throw invalid();
        var liability = gross.subtract(insuranceAmount);
        var balance = liability.subtract(allocationTotals.netAllocated());
        var outcome = balance.signum() > 0 ? SettlementOutcome.ADDITIONAL_PAYMENT_REQUIRED
                : balance.signum() < 0 ? SettlementOutcome.REFUND_DUE : SettlementOutcome.PAID_IN_FULL;
        return new Totals(money(gross, false), money(insuranceAmount, false), money(liability, false),
                money(payments, false), money(refunds, false), money(reversals, false),
                money(allocationTotals.netAllocated(), false), money(unapplied, false),
                money(allocationTotals.unallocatedDeposit(), false), signedMoney(balance), outcome);
    }

    private Map<UUID, ChargeLine> checkedCharges() {
        var byId = new HashMap<UUID, ChargeLine>();
        for (var charge : charges) {
            if (charge.id() == null || !accountId.equals(charge.accountId()) || !patientId.equals(charge.patientId())
                    || charge.status() == null || byId.putIfAbsent(charge.id(), charge) != null) throw invalid();
            money(charge.grossAmount(), false);
        }
        return byId;
    }

    private Map<UUID, TransactionLine> checkedTransactions() {
        var byId = new HashMap<UUID, TransactionLine>();
        for (var transaction : transactions) {
            if (transaction.id() == null || !accountId.equals(transaction.accountId()) || !currency.equals(transaction.currency())
                    || transaction.type() == null || transaction.classification() == null || transaction.status() == null
                    || byId.putIfAbsent(transaction.id(), transaction) != null
                    || ((transaction.type() == PaymentTransactionType.PAYMENT) != (transaction.originalId() == null))) throw invalid();
            money(transaction.amount(), true);
            if (transaction.status() == PaymentTransactionStatus.COMPLETED
                    && (transaction.completedAt() == null || transaction.completedAt().isAfter(observedAt))) throw invalid();
        }
        return byId;
    }

    private Map<UUID, BigDecimal> checkedReturns(Map<UUID, TransactionLine> byId) {
        var returned = new HashMap<UUID, BigDecimal>();
        for (var transaction : transactions) {
            if (transaction.type() == PaymentTransactionType.PAYMENT || transaction.status() != PaymentTransactionStatus.COMPLETED) continue;
            var original = byId.get(transaction.originalId());
            if (original == null || original.type() != PaymentTransactionType.PAYMENT || original.status() != PaymentTransactionStatus.COMPLETED
                    || original.classification() != transaction.classification()
                    || transaction.completedAt().isBefore(original.completedAt())) throw invalid();
            if (returned.merge(original.id(), transaction.amount(), BigDecimal::add).compareTo(original.amount()) > 0) throw invalid();
        }
        return returned;
    }

    private Map<UUID, Map<UUID, BigDecimal>> checkedAllocations(Map<UUID, TransactionLine> transactionById, Map<UUID, ChargeLine> chargeById) {
        var byTransaction = new HashMap<UUID, Map<UUID, BigDecimal>>();
        for (var allocation : allocations) {
            var transaction = transactionById.get(allocation.transactionId());
            if (transaction == null || transaction.status() != PaymentTransactionStatus.COMPLETED
                    || !chargeById.containsKey(allocation.chargeId())) throw invalid();
            var byCharge = byTransaction.computeIfAbsent(transaction.id(), ignored -> new HashMap<>());
            if (byCharge.putIfAbsent(allocation.chargeId(), money(allocation.amount(), true)) != null) throw invalid();
        }
        return byTransaction;
    }

    private record AllocationTotals(BigDecimal netAllocated, BigDecimal unallocatedDeposit) { }

    private AllocationTotals reconcileAllocations(Map<UUID, TransactionLine> transactionById, Map<UUID, ChargeLine> chargeById,
            Map<UUID, Map<UUID, BigDecimal>> byTransaction, Map<UUID, BigDecimal> returnedByOriginal) {
        var returnedAllocation = new HashMap<UUID, Map<UUID, BigDecimal>>();
        var netByCharge = new HashMap<UUID, BigDecimal>();
        BigDecimal unallocatedDeposit = ZERO;
        for (var transaction : transactions) {
            if (transaction.status() != PaymentTransactionStatus.COMPLETED) continue;
            var byCharge = byTransaction.getOrDefault(transaction.id(), Map.of());
            BigDecimal allocated = sum(byCharge);
            if (allocated.compareTo(transaction.amount()) > 0) throw invalid();
            boolean payment = transaction.type() == PaymentTransactionType.PAYMENT;
            for (var allocation : byCharge.entrySet()) {
                if (!payment) checkReturnedAllocation(transaction, allocation, byTransaction, returnedAllocation);
                netByCharge.merge(allocation.getKey(), payment ? allocation.getValue() : allocation.getValue().negate(), BigDecimal::add);
            }
            if (transaction.classification() == PaymentClassification.ADMISSION_DEPOSIT) {
                var unallocated = transaction.amount().subtract(allocated);
                unallocatedDeposit = unallocatedDeposit.add(payment ? unallocated : unallocated.negate());
            }
        }
        checkReturnedUnallocated(transactionById, byTransaction, returnedByOriginal, returnedAllocation);
        BigDecimal netAllocated = ZERO;
        for (var entry : netByCharge.entrySet()) {
            var charge = chargeById.get(entry.getKey());
            if (entry.getValue().signum() < 0 || entry.getValue().compareTo(charge.grossAmount()) > 0) throw invalid();
            if (charge.status() == ChargeStatus.POSTED) netAllocated = netAllocated.add(entry.getValue());
        }
        return new AllocationTotals(netAllocated, unallocatedDeposit);
    }

    private void checkReturnedAllocation(TransactionLine transaction, Map.Entry<UUID, BigDecimal> allocation,
            Map<UUID, Map<UUID, BigDecimal>> byTransaction, Map<UUID, Map<UUID, BigDecimal>> returnedAllocation) {
        var originalAllocations = byTransaction.getOrDefault(transaction.originalId(), Map.of());
        var returned = returnedAllocation.computeIfAbsent(transaction.originalId(), ignored -> new HashMap<>());
        if (returned.merge(allocation.getKey(), allocation.getValue(), BigDecimal::add)
                .compareTo(originalAllocations.getOrDefault(allocation.getKey(), ZERO)) > 0) throw invalid();
    }

    private void checkReturnedUnallocated(Map<UUID, TransactionLine> transactionById, Map<UUID, Map<UUID, BigDecimal>> byTransaction,
            Map<UUID, BigDecimal> returnedByOriginal, Map<UUID, Map<UUID, BigDecimal>> returnedAllocation) {
        for (var original : transactionById.values()) {
            if (original.type() != PaymentTransactionType.PAYMENT || original.status() != PaymentTransactionStatus.COMPLETED) continue;
            var originalAllocated = sum(byTransaction.getOrDefault(original.id(), Map.of()));
            var returnedAllocated = sum(returnedAllocation.getOrDefault(original.id(), Map.of()));
            var returnedUnallocated = returnedByOriginal.getOrDefault(original.id(), ZERO).subtract(returnedAllocated);
            if (returnedUnallocated.compareTo(original.amount().subtract(originalAllocated)) > 0) throw invalid();
        }
    }

    private BigDecimal approvedInsuranceAmount() {
        var byId = new HashMap<UUID, InsuranceLine>();
        BigDecimal total = ZERO;
        for (var adjustment : insurance) {
            if (adjustment.id() == null || !accountId.equals(adjustment.accountId()) || !admissionId.equals(adjustment.admissionId())
                    || adjustment.type() == null || ((adjustment.type() == InsuranceAdjustmentType.APPROVAL) != (adjustment.originalId() == null))
                    || byId.putIfAbsent(adjustment.id(), adjustment) != null) throw invalid();
            var amount = money(adjustment.amount(), false);
            total = total.add(adjustment.type() == InsuranceAdjustmentType.APPROVAL ? amount : amount.negate());
        }
        var reversed = new HashMap<UUID, BigDecimal>();
        for (var adjustment : insurance) {
            if (adjustment.type() != InsuranceAdjustmentType.REVERSAL) continue;
            var original = byId.get(adjustment.originalId());
            if (original == null || original.type() != InsuranceAdjustmentType.APPROVAL
                    || reversed.merge(original.id(), adjustment.amount(), BigDecimal::add).compareTo(original.amount()) > 0) throw invalid();
        }
        return total;
    }

    private BigDecimal completedAmount(PaymentTransactionType type) {
        return transactions.stream().filter(transaction -> transaction.status() == PaymentTransactionStatus.COMPLETED && transaction.type() == type)
                .map(TransactionLine::amount).reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal sum(Map<UUID, BigDecimal> amounts) {
        return amounts.values().stream().reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal value, boolean positive) {
        var checked = signedMoney(value);
        if (checked.signum() < 0 || (positive && checked.signum() == 0)) throw invalid();
        return checked;
    }

    private static BigDecimal signedMoney(BigDecimal value) {
        if (value == null) throw invalid();
        try {
            var checked = value.setScale(2, RoundingMode.UNNECESSARY);
            if (checked.precision() > 19) throw invalid();
            return checked;
        } catch (ArithmeticException exception) { throw invalid(); }
    }

    private static BillingRuleException invalid() {
        return new BillingRuleException("BILLING_SETTLEMENT_LEDGER_INCONSISTENT", "Admission ledger evidence is inconsistent");
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) { throw invalid(); }
    }
}
