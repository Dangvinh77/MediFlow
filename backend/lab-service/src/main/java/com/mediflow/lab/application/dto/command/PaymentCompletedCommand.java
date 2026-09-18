package com.mediflow.lab.application.dto.command;

import java.util.List;
import java.util.UUID;

/** Exact Lab aggregate identifiers covered by one completed Billing payment. */
public record PaymentCompletedCommand(
        UUID eventId,
        List<UUID> labTestIds
) {
    public PaymentCompletedCommand {
        labTestIds = List.copyOf(labTestIds);
    }
}
