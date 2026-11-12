package com.mediflow.pharmacy.application.port.out;

import com.mediflow.pharmacy.application.dto.command.PrescriptionRevisionBillingPayload;
import java.util.UUID;

/** Billing's authoritative fence against money collection during a source revision. */
public interface PrescriptionRevisionBillingPort {
    void prepare(UUID operationId, PrescriptionRevisionBillingPayload payload, String correlationId);
    void commit(UUID operationId, PrescriptionRevisionBillingPayload payload, String correlationId);
    void abort(UUID operationId, PrescriptionRevisionBillingPayload payload, String correlationId);
}
