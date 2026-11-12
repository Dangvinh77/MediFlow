package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.PrescriptionRevisionCommand;
import java.time.Instant;
import java.util.UUID;

public interface PrescriptionRevisionRepositoryPort {
    OperationReceipt prepare(PrescriptionRevisionCommand command, String payloadFingerprint, Instant preparedAt);
    OperationReceipt commit(PrescriptionRevisionCommand command, String payloadFingerprint,
            String snapshotFingerprint, UUID eventId, Instant finalizedAt);
    OperationReceipt abort(PrescriptionRevisionCommand command, String payloadFingerprint, Instant abortedAt);

    record OperationReceipt(UUID prescriptionId, UUID operationId, long revision, String status,
            String payloadFingerprint) { }
}
