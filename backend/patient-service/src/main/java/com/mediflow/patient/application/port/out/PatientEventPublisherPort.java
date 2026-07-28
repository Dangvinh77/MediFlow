package com.mediflow.patient.application.port.out;

import com.mediflow.patient.domain.model.Patient;

/**
 * Announces that this service changed its own state.
 *
 * <p>Takes the domain model, not an event record: the event payload types are an AMQP concern and
 * live in {@code infrastructure/messaging/payload}. The application layer states the intent
 * ("a patient was created"); the adapter decides the wire format, the routing key and the broker.
 */
public interface PatientEventPublisherPort {

    void publishCreated(Patient patient);

    void publishUpdated(Patient patient);
}
