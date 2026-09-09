package com.mediflow.clinical.application.dto.command;

import java.util.UUID;

/** Application projection of the fields Clinical uses from lab.result.created. */
public record LabResultCreatedCommand(
        UUID eventId,
        UUID recordId,
        UUID labId,
        String conclusion
) {
}
