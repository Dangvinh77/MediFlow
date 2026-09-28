package com.mediflow.report.application.dto.command.carefinance;

import java.util.Objects;

public record PaymentRefundedCommand(DecodedCareFinanceEvent event) {
    public PaymentRefundedCommand {
        Objects.requireNonNull(event, "event");
    }
}
