package com.mediflow.pharmacy.application.port.in;

import java.util.UUID;

/** Internal V1 whole-order expiry. Candidate discovery never grants permission to mutate. */
public interface ExpireCarePrescriptionUseCase {
    int expireCare(UUID prescriptionId, String correlationId);
}
