package com.mediflow.inpatient.application.port.out;

import java.util.UUID;

public interface ProcessedEventPort {
    /** Atomically records an event ID, returning false when it was already claimed. */
    boolean tryClaim(UUID eventId, String eventType);
}
