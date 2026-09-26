package com.mediflow.patient.application.port.out;

import java.util.UUID;

public interface CorrelationIdProvider {
    UUID currentOrCreate();
}
