package com.mediflow.clinical.application.dto.command;

import java.util.UUID;

/** Canonical identifiers required to attach a dispensed prescription to a medical record. */
public record PrescriptionFilledCommand(
        UUID eventId,
        UUID recordId,
        UUID prescriptionId
) {}
