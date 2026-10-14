package com.mediflow.clinical.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.clinical.application.event.AppointmentCreatedEvent;
import com.mediflow.clinical.application.event.AppointmentStatusChangedEvent;
import com.mediflow.clinical.application.event.DiagnosisAddedEvent;
import com.mediflow.clinical.application.event.MedicalRecordCreatedEvent;
import com.mediflow.clinical.application.port.out.ClinicalEventPublisherPort;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.LegacyClinicalEventMetadata;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;

/** Publishes clinical domain events only after the surrounding transaction commits. */
@Component
public class ClinicalEventPublisherAdapter implements ClinicalEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;
    private final ClinicalOutboxPort outbox;
    private final boolean durableEventsEnabled;

    @Autowired
    public ClinicalEventPublisherAdapter(
            RabbitTemplate rabbitTemplate,
            ClinicalOutboxPort outbox,
            @Value("${mediflow.clinical.durable-events.enabled:false}") boolean durableEventsEnabled,
            OutpatientMvpMode outpatientMvpMode) {
        this.rabbitTemplate = rabbitTemplate;
        this.outbox = outbox;
        this.durableEventsEnabled = durableEventsEnabled || outpatientMvpMode.enabled();
    }

    ClinicalEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this(rabbitTemplate, null, false, new OutpatientMvpMode(false));
    }

    @Override
    public void publishAppointmentCreated(AppointmentCreatedEvent event) {
        if (durableEventsEnabled) {
            publishDurably(AppointmentCreatedEvent.ROUTING_KEY, event.appointmentId(), event, event.eventId(),
                    event.correlationId(), event.occurredAt());
            return;
        }
        publishAfterCommit(AppointmentCreatedEvent.ROUTING_KEY, event);
    }

    @Override
    public void publishAppointmentStatusChanged(AppointmentStatusChangedEvent event) {
        if (durableEventsEnabled) {
            publishDurably(AppointmentStatusChangedEvent.ROUTING_KEY, event.appointmentId(), event, event.eventId(),
                    event.correlationId(), event.occurredAt());
            return;
        }
        publishAfterCommit(AppointmentStatusChangedEvent.ROUTING_KEY, event);
    }

    @Override
    public void publishMedicalRecordCreated(MedicalRecordCreatedEvent event) {
        if (durableEventsEnabled) {
            publishDurably(MedicalRecordCreatedEvent.ROUTING_KEY, event.recordId(), event, event.eventId(),
                    event.correlationId(), event.occurredAt());
            return;
        }
        publishAfterCommit(MedicalRecordCreatedEvent.ROUTING_KEY, event);
    }

    @Override
    public void publishDiagnosisAdded(DiagnosisAddedEvent event) {
        if (durableEventsEnabled) {
            publishDurably(DiagnosisAddedEvent.ROUTING_KEY, event.recordId(), event, event.eventId(),
                    event.correlationId(), event.occurredAt());
            return;
        }
        publishAfterCommit(DiagnosisAddedEvent.ROUTING_KEY, event);
    }

    private void publishDurably(
            String routingKey,
            UUID aggregateId,
            Object payload,
            UUID eventId,
            String correlationId,
            Instant occurredAt) {
        Message message = rabbitTemplate.getMessageConverter().toMessage(payload, new MessageProperties());
        if (message == null) {
            throw new IllegalStateException("Rabbit message converter returned no " + routingKey + " payload");
        }
        outbox.appendLegacyEvent(new LegacyClinicalEventMetadata(
                eventId, routingKey, correlationId, aggregateId, occurredAt), message.getBody());
    }

    private void publishAfterCommit(String routingKey, Object payload) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishNow(routingKey, payload);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publishNow(routingKey, payload);
                    }
                });
    }

    private void publishNow(String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, routingKey, payload);
    }
}
