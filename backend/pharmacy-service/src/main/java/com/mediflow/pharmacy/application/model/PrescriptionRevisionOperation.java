package com.mediflow.pharmacy.application.model;

import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.PrescriptionRevisionBillingPayload;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable receipt for one revision across Billing and Pharmacy service boundaries. */
public record PrescriptionRevisionOperation(
        UUID operationId, UUID prescriptionId, int expectedRevision,
        ActorIdentity actor, String correlationId, String commandFingerprint,
        List<PrescriptionLineRequest> requestedLines,
        PrescriptionRevisionBillingPayload billingPayload,
        Status status, String failureCode, String failureMessage,
        Instant createdAt, Instant updatedAt) {
    public PrescriptionRevisionOperation {
        if (operationId == null || prescriptionId == null || expectedRevision < 0 || actor == null
                || correlationId == null || correlationId.isBlank()
                || commandFingerprint == null || !commandFingerprint.matches("[0-9a-f]{64}")
                || requestedLines == null || requestedLines.isEmpty() || billingPayload == null
                || status == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Complete durable revision operation is required");
        }
        requestedLines = List.copyOf(requestedLines);
    }

    public enum Status { PENDING, PREPARED, APPLIED, COMMITTED, ABORTING, ABORTED }

    public PrescriptionRevisionOperation transition(Status next, String code, String message, Instant at) {
        if (at == null || !allowed(status, next)) {
            throw new IllegalStateException("Invalid revision operation transition " + status + " -> " + next);
        }
        return new PrescriptionRevisionOperation(operationId, prescriptionId, expectedRevision, actor,
                correlationId, commandFingerprint, requestedLines, billingPayload, next, code, message,
                createdAt, at);
    }

    private static boolean allowed(Status current, Status next) {
        return switch (current) {
            case PENDING -> next == Status.PREPARED || next == Status.ABORTING;
            case PREPARED -> next == Status.APPLIED || next == Status.ABORTING;
            case APPLIED -> next == Status.COMMITTED;
            case ABORTING -> next == Status.ABORTED;
            case COMMITTED, ABORTED -> false;
        };
    }
}
