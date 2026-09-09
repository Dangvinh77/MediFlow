package com.mediflow.clinical.infrastructure.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.clinical.application.event.AppointmentCreatedEvent;
import com.mediflow.clinical.application.event.AppointmentStatusChangedEvent;
import com.mediflow.clinical.application.event.DiagnosisAddedEvent;
import com.mediflow.clinical.application.event.MedicalRecordCreatedEvent;
import com.mediflow.clinical.application.port.out.ClinicalEventPublisherPort;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;

/** Publishes clinical domain events only after the surrounding transaction commits. */
@Component
public class ClinicalEventPublisherAdapter implements ClinicalEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    public ClinicalEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishAppointmentCreated(AppointmentCreatedEvent event) {
        publishAfterCommit(AppointmentCreatedEvent.ROUTING_KEY, event);
    }

    @Override
    public void publishAppointmentStatusChanged(AppointmentStatusChangedEvent event) {
        publishAfterCommit(AppointmentStatusChangedEvent.ROUTING_KEY, event);
    }

    @Override
    public void publishMedicalRecordCreated(MedicalRecordCreatedEvent event) {
        publishAfterCommit(MedicalRecordCreatedEvent.ROUTING_KEY, event);
    }

    @Override
    public void publishDiagnosisAdded(DiagnosisAddedEvent event) {
        publishAfterCommit(DiagnosisAddedEvent.ROUTING_KEY, event);
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
