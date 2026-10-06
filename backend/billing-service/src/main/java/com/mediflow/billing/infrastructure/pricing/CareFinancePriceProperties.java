package com.mediflow.billing.infrastructure.pricing;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bảng giá V2 theo price code, đọc từ {@code billing.care-finance.prices}. Mặc định rỗng: price code
 * chưa được khai báo sẽ bị từ chối, không tự thành 0 (CONTRACT-SURGERY-BILLING-01, mục catalog).
 */
@Component
@ConfigurationProperties(prefix = "billing.care-finance")
public class CareFinancePriceProperties {

    private Map<String, Price> prices = Map.of();

    public Map<String, Price> getPrices() {
        return prices;
    }

    public void setPrices(Map<String, Price> prices) {
        this.prices = prices == null ? Map.of() : prices;
    }

    public record Price(String description, BigDecimal unitAmount) {
    }
}
