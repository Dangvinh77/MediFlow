package com.mediflow.clinical.application.port.in;

import java.util.UUID;

/** Consumer must resolve real record correlation and deduplicate. Store attachments separately from symptoms. */
public interface AttachExternalResultUseCase {
    void attachLabResult(UUID recordId, UUID labTestId, String conclusion);
    void attachPrescription(UUID recordId, UUID prescriptionId);
}
