package com.mediflow.billing.application.dto.command;

import java.util.Set;

/** One immutable terminal operation per Rx; its timestamp is snapshot.createdAt(), not creation time. */
public record PrescriptionTerminalCommand(String eventType, PrescriptionChargeCommand snapshot) {
    public PrescriptionTerminalCommand {
        if (snapshot == null || !Set.of("prescription.filled", "prescription.cancelled", "prescription.expired",
                "prescription.dispense.failed").contains(eventType))
            throw new IllegalArgumentException("Invalid prescription terminal fact");
    }
    public boolean filled() { return "prescription.filled".equals(eventType); }
}
