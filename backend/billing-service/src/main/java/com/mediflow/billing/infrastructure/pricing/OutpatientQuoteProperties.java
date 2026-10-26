package com.mediflow.billing.infrastructure.pricing;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.outpatient-quotes")
public record OutpatientQuoteProperties(String catalogueVersion, String policyVersion, List<SupportItem> items) {
    public OutpatientQuoteProperties {
        if (catalogueVersion == null || catalogueVersion.isBlank() || policyVersion == null || policyVersion.isBlank())
            throw new IllegalArgumentException("Enabled outpatient quotes require explicit catalogue and policy versions");
        items = items == null ? List.of() : List.copyOf(items);
        var keys = new HashSet<String>();
        for (var item : items) {
            if (item.sourceType() == null || !List.of("EXAM", "LAB_TEST", "PRESCRIPTION").contains(item.sourceType())
                    || item.priceCode() == null || item.priceCode().isBlank() || item.supportRate() == null
                    || item.supportRate().signum() < 0 || item.supportRate().compareTo(BigDecimal.ONE) > 0
                    || !keys.add(item.sourceType() + ":" + item.priceCode()))
                throw new IllegalArgumentException("Invalid or duplicate explicit outpatient support mapping");
        }
    }
    public BigDecimal rateFor(String sourceType, String priceCode) {
        return items.stream().filter(i -> i.sourceType().equals(sourceType) && i.priceCode().equals(priceCode))
                .map(SupportItem::supportRate).findFirst().orElse(BigDecimal.ZERO);
    }
    public record SupportItem(String sourceType, String priceCode, BigDecimal supportRate) {}
}
