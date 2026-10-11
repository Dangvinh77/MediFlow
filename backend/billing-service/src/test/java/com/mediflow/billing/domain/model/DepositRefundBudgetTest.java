package com.mediflow.billing.domain.model;

import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepositRefundBudgetTest {
    @Test void deposit100Allocated80Refunded20HasNoUnusedCash() {
        assertThat(DepositRefundBudget.remaining(money("100"), money("20"), money("80")))
                .isEqualByComparingTo("0.00");
    }
    @Test void partialUnusedRefundKeepsExactRemainingBudget() {
        assertThat(DepositRefundBudget.remaining(money("100"), money("12.34"), money("80")))
                .isEqualByComparingTo("7.66");
    }
    @Test void allocatedCashCannotBeReturnedByUnusedDepositPath() {
        assertThatThrownBy(() -> DepositRefundBudget.remaining(money("100"), money("20.01"), money("80")))
                .hasMessage("Refund exceeds this original deposit's unused cash");
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"-1", "0.001", "100000000000000000"})
    void corruptMoneyFailsClosed(String value) {
        assertThatThrownBy(() -> DepositRefundBudget.remaining(money("100"), value == null ? null : money(value), money("80")))
                .isInstanceOf(BillingRuleException.class);
    }
    @Test void zeroOriginalIsNotAReceipt() {
        assertThatThrownBy(() -> DepositRefundBudget.remaining(money("0"), money("0"), money("0")))
                .isInstanceOf(BillingRuleException.class);
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
}
