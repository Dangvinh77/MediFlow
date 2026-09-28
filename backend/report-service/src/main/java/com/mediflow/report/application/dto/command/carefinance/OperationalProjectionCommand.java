package com.mediflow.report.application.dto.command.carefinance;

import java.util.Objects;

public record OperationalProjectionCommand(DecodedCareFinanceEvent event) {
    public OperationalProjectionCommand {
        Objects.requireNonNull(event, "event");
    }
}
