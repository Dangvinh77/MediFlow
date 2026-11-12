package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.PrescriptionRevisionCommand;

public interface PrescriptionRevisionFingerprintPort {
    String payloadFingerprint(PrescriptionRevisionCommand command);
    String snapshotFingerprint(PrescriptionRevisionCommand command);
}
