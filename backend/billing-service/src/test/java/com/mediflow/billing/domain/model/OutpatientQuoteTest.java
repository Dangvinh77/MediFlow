package com.mediflow.billing.domain.model;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutpatientQuoteTest {
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    @Test void sumsRoundedSupportPerLineAndKeepsGrossSeparateFromCashLiability() {
        var quote = quote(List.of(line("EXAM", "OUTPATIENT_EXAM", "100000", "0.5"),
                line("LAB_TEST", "LAB-CBC", "120000", "0.7")));
        assertThat(quote.grossAmount()).isEqualByComparingTo("220000");
        assertThat(quote.supportAmount()).isEqualByComparingTo("134000");
        assertThat(quote.patientPayable()).isEqualByComparingTo("86000");
        assertThat(quote.currency()).isEqualTo("VND");
        assertThat(quote.lines().getFirst().policyVersion()).isEqualTo("DEMO-BHYT-2026-10-25");
    }

    @Test void roundHalfUpEachLineBeforeSummationAndRejectClientMutations() {
        var input = new java.util.ArrayList<>(List.of(line("LAB_TEST", "ODD-A", "3", "0.5"),
                line("LAB_TEST", "ODD-B", "3", "0.5")));
        var quote = quote(input);
        input.clear();
        assertThat(quote.supportAmount()).isEqualByComparingTo("4");
        assertThat(quote.patientPayable()).isEqualByComparingTo("2");
        assertThatThrownBy(() -> quote.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void zeroLiabilityHasNoCashAndExpiresAtExactBoundary() {
        var quote = quote(List.of(line("EXAM", "FULL_SUPPORT", "100000", "1")));
        assertThat(quote.patientPayable()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.requiresPatientPayment()).isFalse();
        assertThat(quote.isPayableAt(NOW.plusSeconds(59))).isTrue();
        assertThat(quote.isPayableAt(NOW.plusSeconds(60))).isFalse();
        assertThat(quote.isPayableAt(NOW.minusSeconds(1))).isFalse();
    }

    @Test void rejectsNegativeOverOneMissingVersionInvalidQuantityAndDuplicateSourceItems() {
        for (String rate : List.of("-0.1", "1.01"))
            assertThatThrownBy(() -> line("EXAM", "CODE", "100", rate)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutpatientQuoteLine.calculate(UUID.randomUUID(), "EXAM", "CODE", "", "policy",
                "Khám", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutpatientQuoteLine.calculate(UUID.randomUUID(), "EXAM", "CODE", "catalogue", "policy",
                "Khám", BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        var line = line("EXAM", "CODE", "100", "0.5");
        assertThatThrownBy(() -> quote(List.of(line, line))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void historicalQuoteKeepsOriginalPolicyAfterNewQuoteUsesNewRate() {
        var old = quote(List.of(line("EXAM", "OUTPATIENT_EXAM", "100000", "0.5")));
        var next = quote(List.of(line("EXAM", "OUTPATIENT_EXAM", "100000", "0.7")));
        assertThat(old.patientPayable()).isEqualByComparingTo("50000");
        assertThat(next.patientPayable()).isEqualByComparingTo("30000");
    }

    private static OutpatientQuote quote(List<OutpatientQuoteLine> lines) {
        return new OutpatientQuote(UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT_VISIT", UUID.randomUUID(),
                lines, NOW, NOW.plusSeconds(60));
    }
    private static OutpatientQuoteLine line(String type, String code, String amount, String rate) {
        return OutpatientQuoteLine.calculate(UUID.randomUUID(), type, code, "DEMO-CATALOGUE-2026-10-26",
                "DEMO-BHYT-2026-10-25", "Dịch vụ demo", BigDecimal.ONE, new BigDecimal(amount), new BigDecimal(rate));
    }
}
