package com.mediflow.billing.domain.model;

import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Returning unused deposit cash must never undo a funded charge or borrow another receipt. */
public final class DepositRefundBudget {
    private DepositRefundBudget() { }

    /** Completed returns include the proposed refund, appended in the same transaction. */
    public static BigDecimal remaining(BigDecimal originalAmount, BigDecimal completedReturns,
            BigDecimal netAllocatedAmount) {
        var original = money(originalAmount);
        var returned = money(completedReturns);
        var allocated = money(netAllocatedAmount);
        if (original.signum() == 0) throw invalid();
        var remaining = original.subtract(returned).subtract(allocated);
        if (remaining.signum() < 0) throw new BillingRuleException(
                "BILLING_DEPOSIT_REFUND_EXCEEDS_UNALLOCATED", "Refund exceeds this original deposit's unused cash");
        return remaining;
    }

    private static BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) throw invalid();
        try {
            var exact = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (exact.precision() > 19) throw invalid();
            return exact;
        } catch (ArithmeticException failure) { throw invalid(); }
    }

    private static BillingRuleException invalid() {
        return new BillingRuleException("BILLING_REFUND_ALLOCATION_MISMATCH", "Invalid original deposit budget");
    }
}
