package com.mediflow.patient.infrastructure.messaging;

import com.mediflow.patient.application.event.PatientCreatedEvent;
import com.mediflow.patient.application.event.PatientUpdatedEvent;
import com.mediflow.patient.application.port.out.PatientEventPublisherPort;
import com.mediflow.patient.infrastructure.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Publishes write events only after the surrounding database transaction commits. */
@Component
public class PatientEventPublisherAdapter implements PatientEventPublisherPort {
    private static final Logger log = LoggerFactory.getLogger(PatientEventPublisherAdapter.class);
    private final RabbitTemplate rabbitTemplate;

    public PatientEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishCreated(PatientCreatedEvent event) {
        publishAfterCommit(RabbitConfig.RK_PATIENT_CREATED, event);
    }

    @Override
    public void publishUpdated(PatientUpdatedEvent event) {
        publishAfterCommit(RabbitConfig.RK_PATIENT_UPDATED, event);
    }

    private void publishAfterCommit(String routingKey, Object payload) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishNow(routingKey, payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishNow(routingKey, payload);
            }
        });
    }

    private void publishNow(String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, payload);
        log.info("Published patient event routingKey={}", routingKey);
    }
}
