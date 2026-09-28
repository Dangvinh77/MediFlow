package com.mediflow.report.application.dto.command.carefinance;

import java.util.Objects;

public record SettlementCompletedCommand(DecodedCareFinanceEvent event) {
    public SettlementCompletedCommand {
        Objects.requireNonNull(event, "event");
    }
}
