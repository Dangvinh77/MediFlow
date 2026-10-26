package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.application.event.DomainEventEnvelope;

public interface ClinicalOutboxPort {
    void append(DomainEventEnvelope<?> event);

    /** Persist the exact serialized bytes for newly versioned outpatient events. */
    void appendRawOutpatientEvent(DomainEventEnvelope<?> event);

    /** Persist an existing flat Clinical event's exact wire bytes and routing metadata. */
    void appendLegacyEvent(LegacyClinicalEventMetadata metadata, byte[] wirePayload);
}
