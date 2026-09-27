package com.mediflow.inpatient.application.dto.event;

import java.math.BigDecimal;

public record DepositSuggestion(String priceCode, BigDecimal amount, String reason) {
    public DepositSuggestion {
        if (priceCode == null || priceCode.isBlank() || amount == null || amount.signum() <= 0
                || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Deposit suggestion must be configured with a price and reason");
        }
    }
}
