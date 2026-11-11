package com.mediflow.clinical.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** Consumer must resolve real record correlation and deduplicate. Store attachments separately from symptoms. */
public interface AttachExternalResultUseCase {
    default boolean attachLabResult(UUID recordId, UUID labTestId, String conclusion) {
        return attachLabResult(recordId, labTestId, conclusion, null);
    }

    /** {@code reportedAt} is when Lab produced the result; {@code null} falls back to the arrival time. */
    boolean attachLabResult(UUID recordId, UUID labTestId, String conclusion, Instant reportedAt);
    boolean attachPrescription(UUID recordId, UUID prescriptionId);
}
