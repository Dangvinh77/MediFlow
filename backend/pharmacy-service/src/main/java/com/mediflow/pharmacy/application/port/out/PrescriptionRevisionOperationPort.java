package com.mediflow.pharmacy.application.port.out;

import com.mediflow.pharmacy.application.model.PrescriptionRevisionOperation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable revision-intent receipts used for idempotency and cross-service recovery. */
public interface PrescriptionRevisionOperationPort {
    Optional<PrescriptionRevisionOperation> findByIdForUpdate(UUID operationId);
    Optional<PrescriptionRevisionOperation> findById(UUID operationId);
    Optional<PrescriptionRevisionOperation> findActiveByPrescriptionForUpdate(UUID prescriptionId);
    Optional<PrescriptionRevisionOperation> findActiveByPrescription(UUID prescriptionId);
    PrescriptionRevisionOperation create(PrescriptionRevisionOperation operation);
    PrescriptionRevisionOperation update(PrescriptionRevisionOperation operation, PrescriptionRevisionOperation.Status expectedStatus);
    List<UUID> findActiveIds(int limit);
}
