package com.mediflow.report.application.dto.command.carefinance;

import java.util.Objects;

public record PaymentCompletedCommand(DecodedCareFinanceEvent event) {
    public PaymentCompletedCommand {
        Objects.requireNonNull(event, "event");
    }
}
