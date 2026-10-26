package com.mediflow.billing.infrastructure.pricing;

import com.mediflow.billing.application.port.out.OutpatientQuoteCatalogPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.domain.model.OutpatientQuoteLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class OutpatientQuoteCatalogAdapter implements OutpatientQuoteCatalogPort {
    private final PriceCatalogPort prices;
    private final OutpatientQuoteProperties policy;
    public OutpatientQuoteCatalogAdapter(PriceCatalogPort prices, OutpatientQuoteProperties policy) {
        this.prices = prices; this.policy = policy;
    }
    @Override public OutpatientQuoteLine requireLine(UUID sourceId, String sourceType, String priceCode,
            BigDecimal quantity, Instant at) {
        if (at == null) throw new IllegalArgumentException("Quote time is required");
        var price = prices.requireActive(priceCode, at);
        return OutpatientQuoteLine.calculate(sourceId, sourceType, priceCode, policy.catalogueVersion(),
                policy.policyVersion(), price.description(), quantity, price.unitAmount(), policy.rateFor(sourceType, priceCode));
    }
}
