package com.mediflow.organization.application.port.out;

import java.util.UUID;

/** Provides the request correlation id without coupling application code to HTTP. */
public interface CorrelationIdProvider {
    UUID currentOrCreate();
}
