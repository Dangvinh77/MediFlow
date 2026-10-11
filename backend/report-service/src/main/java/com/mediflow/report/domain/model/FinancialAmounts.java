package com.mediflow.report.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cash/revenue/refunds are flows; liability/receivable are closing stocks, NEVER summed over days. */
public record FinancialAmounts(BigDecimal cashReceived, BigDecimal depositLiability, BigDecimal earnedRevenue,
        BigDecimal refunds, BigDecimal outstandingReceivable) {
    public FinancialAmounts {
        cashReceived=money(cashReceived); depositLiability=money(depositLiability);
        earnedRevenue=money(earnedRevenue); refunds=money(refunds); outstandingReceivable=money(outstandingReceivable);
    }
    public FinancialAmounts through(FinancialAmounts closingDay) {
        return new FinancialAmounts(cashReceived.add(closingDay.cashReceived), closingDay.depositLiability,
                earnedRevenue.add(closingDay.earnedRevenue), refunds.add(closingDay.refunds), closingDay.outstandingReceivable);
    }
    private static BigDecimal money(BigDecimal value) {
        try {
            if (value==null || value.signum()<0) throw new IllegalArgumentException("Complete non-negative financial amounts required");
            value=value.setScale(2, RoundingMode.UNNECESSARY);
            if (value.precision()>19) throw new IllegalArgumentException("Financial aggregate exceeds NUMERIC(19,2)");
            return value;
        } catch (ArithmeticException exception) { throw new IllegalArgumentException("Exact financial scale required"); }
    }
}
