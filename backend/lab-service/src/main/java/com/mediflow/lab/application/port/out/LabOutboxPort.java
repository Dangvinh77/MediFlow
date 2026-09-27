package com.mediflow.lab.application.port.out;

import com.mediflow.lab.application.event.DomainEventEnvelope;

/** Transactional append boundary for V2 Lab domain events. */
public interface LabOutboxPort {

    void append(DomainEventEnvelope<?> event);
}
