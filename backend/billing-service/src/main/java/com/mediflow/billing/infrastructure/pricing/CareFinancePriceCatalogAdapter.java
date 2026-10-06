package com.mediflow.billing.infrastructure.pricing;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.domain.exception.BillingRuleException;

/** Adapter cho {@link PriceCatalogPort} dựa trên cấu hình {@link CareFinancePriceProperties}. */
@Component
public class CareFinancePriceCatalogAdapter implements PriceCatalogPort {

    private final CareFinancePriceProperties properties;

    public CareFinancePriceCatalogAdapter(CareFinancePriceProperties properties) {
        this.properties = properties;
    }

    @Override
    public PriceSnapshot requireActive(String priceCode, Instant at) {
        CareFinancePriceProperties.Price price = properties.getPrices().get(priceCode);
        if (price == null || price.unitAmount() == null) {
            throw new BillingRuleException("BILLING_PRICE_CODE_UNKNOWN",
                    "Price code chưa có trong bảng giá: " + priceCode);
        }
        return new PriceSnapshot(price.description(), price.unitAmount());
    }
}
