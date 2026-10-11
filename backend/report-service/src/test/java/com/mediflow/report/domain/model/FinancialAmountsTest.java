package com.mediflow.report.domain.model;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class FinancialAmountsTest {
    private FinancialAmounts amounts(String cash, String liability, String earned, String refunds, String debt) {
        return new FinancialAmounts(new BigDecimal(cash), new BigDecimal(liability), new BigDecimal(earned),
                new BigDecimal(refunds), new BigDecimal(debt));
    }
    @Test void accumulate_twoDays_sumsFlowsButUsesLastClosingStocks() {
        var result = amounts("60", "60", "0", "0", "40").through(amounts("40", "0", "100", "15", "15"));
        assertThat(result.cashReceived()).isEqualByComparingTo("100");
        assertThat(result.earnedRevenue()).isEqualByComparingTo("100");
        assertThat(result.refunds()).isEqualByComparingTo("15");
        assertThat(result.depositLiability()).isZero();
        assertThat(result.outstandingReceivable()).isEqualByComparingTo("15");
    }
    @ParameterizedTest @ValueSource(strings={"-1", "0.001", "100000000000000000"})
    void construct_invalidMoney_rejectsEachMetric(String bad) {
        for (int index=0; index<5; index++) {
            BigDecimal[] values = {BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE};
            values[index] = new BigDecimal(bad);
            assertThatThrownBy(() -> new FinancialAmounts(values[0],values[1],values[2],values[3],values[4]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void construct_missingMetric_isNotZero() {
        assertThatThrownBy(() -> new FinancialAmounts(BigDecimal.ONE,null,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void construct_depositOnly_doesNotDeriveEarnedRevenue() {
        var value=amounts("100","100","0","0","0");
        assertThat(value.earnedRevenue()).isZero(); assertThat(value.depositLiability()).isEqualByComparingTo("100");
    }
    @Test void accumulate_largeBoundedPeriod_neverRoundsOrOverflowsSilently() {
        var value=amounts("99999999999999999.99","0","0","0","0");
        assertThatThrownBy(() -> value.through(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
