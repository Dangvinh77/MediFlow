package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.application.event.DomainEventEnvelope;

public interface ClinicalOutboxPort {
    void append(DomainEventEnvelope<?> event);
}
