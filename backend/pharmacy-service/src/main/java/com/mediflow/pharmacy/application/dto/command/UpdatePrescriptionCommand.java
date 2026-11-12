package com.mediflow.pharmacy.application.dto.command;

import com.mediflow.pharmacy.application.dto.request.UpdatePrescriptionRequest;
import java.util.Objects;
import java.util.UUID;

/** Stable edit command whose identity and actor come from authenticated request metadata. */
public record UpdatePrescriptionCommand(UUID prescriptionId, UUID operationId,
        UpdatePrescriptionRequest request, ActorIdentity actor, String correlationId) {
    public UpdatePrescriptionCommand {
        Objects.requireNonNull(prescriptionId, "prescriptionId is required");
        Objects.requireNonNull(operationId, "operationId is required");
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(actor, "actor is required");
        correlationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
    }
}
