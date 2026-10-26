package com.mediflow.billing.infrastructure.pricing;

import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OutpatientQuoteCatalogAdapterTest {
    @Test void supportAppliesOnlyToExactConfiguredSourceAndPriceCode() {
        var policy = new OutpatientQuoteProperties("catalogue-1", "DEMO-BHYT-2026-10-25", List.of(
                new OutpatientQuoteProperties.SupportItem("EXAM", "EXAM-A", new BigDecimal("0.5"))));
        var adapter = new OutpatientQuoteCatalogAdapter((code, at) -> new PriceCatalogPort.PriceSnapshot("Khám", new BigDecimal("100000")), policy);
        assertThat(adapter.requireLine(UUID.randomUUID(), "EXAM", "EXAM-A", BigDecimal.ONE, Instant.now()).patientPayable())
                .isEqualByComparingTo("50000");
        assertThat(adapter.requireLine(UUID.randomUUID(), "LAB_TEST", "EXAM-A", BigDecimal.ONE, Instant.now()).supportRate()).isZero();
        assertThat(adapter.requireLine(UUID.randomUUID(), "EXAM", "exam-a", BigDecimal.ONE, Instant.now()).supportRate()).isZero();
    }
    @Test void unknownCatalogueCannotBecomeFreeAndBadMappingsFailConfiguration() {
        var policy = new OutpatientQuoteProperties("catalogue-1", "policy-1", List.of());
        var adapter = new OutpatientQuoteCatalogAdapter((code, at) -> { throw new BillingRuleException("BILLING_PRICE_CODE_UNKNOWN", "Unknown"); }, policy);
        assertThatThrownBy(() -> adapter.requireLine(UUID.randomUUID(), "EXAM", "UNKNOWN", BigDecimal.ONE, Instant.now()))
                .isInstanceOf(BillingRuleException.class);
        assertThatThrownBy(() -> new OutpatientQuoteProperties("", "policy", List.of())).isInstanceOf(IllegalArgumentException.class);
        var item = new OutpatientQuoteProperties.SupportItem("EXAM", "EXAM-A", BigDecimal.ONE);
        assertThatThrownBy(() -> new OutpatientQuoteProperties("catalogue", "policy", List.of(item, item)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
