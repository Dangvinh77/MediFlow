package com.mediflow.clinical.application.port.out;

import java.util.UUID;

/** Supplies the correlation id for the current request or application flow. */
public interface CorrelationIdProvider {

    /**
     * Returns the id bound to the current execution context, creating one when none exists.
     */
    UUID currentOrCreate();
}
