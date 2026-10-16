package com.mediflow.clinical.application.dto.command;

import java.util.UUID;

/** Canonical identifiers required to attach a dispensed prescription to a medical record. */
public record PrescriptionFilledCommand(
        UUID eventId,
        UUID recordId,
        UUID prescriptionId,
        int sourceVersion,
        UUID patientId,
        UUID departmentId,
        String careEpisodeType,
        UUID careEpisodeId,
        UUID dispenseId
) {
    public PrescriptionFilledCommand(UUID eventId, UUID recordId, UUID prescriptionId) {
        this(eventId, recordId, prescriptionId, 0, null, null, null, null, null);
    }
}
