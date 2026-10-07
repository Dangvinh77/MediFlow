package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.application.dto.SurgeryCreationOutcome;
import java.util.Optional;
import java.util.UUID;

/** Global request fence shared by all channels/replicas. Claim and all effects share one write transaction. */
public interface SurgeryCreationReceiptPort {
    Optional<SurgeryCreationOutcome> find(UUID requestId, String intentFingerprint);
    Claim claim(UUID requestId, String intentFingerprint);
    void complete(UUID receiptId, SurgeryCreationOutcome outcome);

    record Claim(UUID receiptId, SurgeryCreationOutcome outcome) { }
}
