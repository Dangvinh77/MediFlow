package com.mediflow.billing.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.application.port.out.PriceCatalogPort.PriceSnapshot;
import com.mediflow.billing.domain.exception.BillingRuleException;

class CareFinancePriceCatalogAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

    @Test
    void requireActive_returnsDeclaredPrice() {
        CareFinancePriceCatalogAdapter adapter = adapterWith(Map.of(
                "SURGERY_APPENDECTOMY",
                new CareFinancePriceProperties.Price("Cắt ruột thừa", new BigDecimal("1500000.00"))));

        PriceSnapshot snapshot = adapter.requireActive("SURGERY_APPENDECTOMY", NOW);

        assertThat(snapshot.description()).isEqualTo("Cắt ruột thừa");
        assertThat(snapshot.unitAmount()).isEqualByComparingTo("1500000.00");
    }

    @Test
    void requireActive_rejectsUnknownPriceCode_insteadOfDefaultingToZero() {
        CareFinancePriceCatalogAdapter adapter = adapterWith(Map.of());

        assertThatThrownBy(() -> adapter.requireActive("NOT_IN_CATALOG", NOW))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("NOT_IN_CATALOG");
    }

    @Test
    void requireActive_rejectsEntryWithoutAmount() {
        CareFinancePriceCatalogAdapter adapter = adapterWith(Map.of(
                "EXAM_BASIC", new CareFinancePriceProperties.Price("Khám", null)));

        assertThatThrownBy(() -> adapter.requireActive("EXAM_BASIC", NOW))
                .isInstanceOf(BillingRuleException.class);
    }

    private static CareFinancePriceCatalogAdapter adapterWith(
            Map<String, CareFinancePriceProperties.Price> prices) {
        CareFinancePriceProperties properties = new CareFinancePriceProperties();
        properties.setPrices(prices);
        return new CareFinancePriceCatalogAdapter(properties);
    }
}
