package com.mediflow.surgery.domain.model;

import java.math.BigDecimal;

/** Planned catalogue references, not amounts. Billing resolves prices at its authoritative boundary. */
public record SurgeryPlannedItem(String itemCode, String priceCode, BigDecimal quantity) {
    public SurgeryPlannedItem {
        if (itemCode==null || !itemCode.matches("[A-Za-z0-9._-]{1,64}")
                || priceCode==null || !priceCode.matches("[A-Za-z0-9._-]{1,64}")
                || quantity==null || quantity.signum()<=0 || quantity.stripTrailingZeros().scale()>4
                || quantity.precision()-quantity.scale()>15) {
            throw new IllegalArgumentException("Invalid planned Surgery catalogue item");
        }
        quantity=quantity.stripTrailingZeros();
    }
}
