package com.mediflow.lab.infrastructure.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.lab.application.event.LabRequestCreatedEvent;
import com.mediflow.lab.application.event.LabResultCreatedEvent;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.infrastructure.config.RabbitConfig;

/** Publishes lab domain events only after the surrounding transaction commits. */
@Component
public class LabEventPublisherAdapter implements LabEventPublisherPort {

    private static final String REQUEST_CREATED = "lab.request.created";
    private static final String RESULT_CREATED = "lab.result.created";

    private final RabbitTemplate rabbitTemplate;

    public LabEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishRequestCreated(LabRequestCreatedEvent event) {
        publishAfterCommit(REQUEST_CREATED, event);
    }

    @Override
    public void publishResultCreated(LabResultCreatedEvent event) {
        publishAfterCommit(RESULT_CREATED, event);
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
