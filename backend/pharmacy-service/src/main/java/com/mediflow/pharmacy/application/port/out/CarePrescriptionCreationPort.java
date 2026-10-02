package com.mediflow.pharmacy.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** A local command fence/receipt, committed atomically with prescription creation. */
public interface CarePrescriptionCreationPort {
    Optional<UUID> claim(UUID commandId, UUID accountId, String fingerprint);
    void complete(UUID commandId, UUID prescriptionId);
}
