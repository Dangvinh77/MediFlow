package com.mediflow.patient.infrastructure.messaging;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import java.nio.charset.StandardCharsets;
import com.mediflow.patient.application.event.PatientCreatedEvent;
import com.mediflow.patient.application.event.PatientUpdatedEvent;
import com.mediflow.patient.application.port.out.PatientEventPublisherPort;
import com.mediflow.patient.infrastructure.config.RabbitConfig;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

/** Event intent commits atomically with the Patient write in the Patient store. */
@Component
@Transactional(transactionManager = "patientTransactionManager", propagation = Propagation.MANDATORY)
public class PatientEventPublisherAdapter implements PatientEventPublisherPort {
    private final PatientOutboxStore store;
    private final MessageConverter converter;

    public PatientEventPublisherAdapter(PatientOutboxStore store, MessageConverter converter) {
        this.store = store;
        this.converter = converter;
    }

    @Override
    public void publishCreated(PatientCreatedEvent event) {
        append(event.eventId(), event.occurredAt(), event.correlationId(), RabbitConfig.RK_PATIENT_CREATED, event);
    }

    @Override
    public void publishUpdated(PatientUpdatedEvent event) {
        append(event.eventId(), event.occurredAt(), event.correlationId(), RabbitConfig.RK_PATIENT_UPDATED, event);
    }

    private void append(UUID id, Instant occurredAt, String correlationId, String routingKey, Object event) {
        // Reuse the live Rabbit serializer: date representation and field names stay unchanged.
        byte[] bytes = converter.toMessage(event, new MessageProperties()).getBody();
        store.append(id, occurredAt, correlationId, routingKey, new String(bytes, StandardCharsets.UTF_8));
    }
}
