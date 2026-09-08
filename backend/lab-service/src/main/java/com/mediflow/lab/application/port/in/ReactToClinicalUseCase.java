package com.mediflow.lab.application.port.in;

import java.util.UUID;

/**
 * Contract for future event consumers. Payment payloads currently do not carry lab identifiers;
 * implementations must wait for an explicit lab/test id and must not infer one from invoiceId or
 * recordId.
 */
public interface ReactToClinicalUseCase {

    void autoCreateFromRecord(UUID recordId, UUID patientId, UUID departmentId, String labType);

    void markPaid(UUID testId);
}
