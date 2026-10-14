package com.mediflow.organization.infrastructure.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.event.DepartmentCreatedEvent;
import com.mediflow.organization.application.event.StaffCreatedEvent;
import com.mediflow.organization.application.event.StaffDepartmentChangedEvent;
import com.mediflow.organization.infrastructure.config.RabbitMqConfig;

@Component
public class RabbitMqEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqEventPublisher(
            RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishDepartmentCreated(DepartmentCreatedEvent event) {
        publishAfterCommit(DepartmentCreatedEvent.ROUTING_KEY, event);
    }

    @Override
    public void publishStaffCreated(StaffCreatedEvent event) {
        publishAfterCommit(StaffCreatedEvent.ROUTING_KEY, event);
    }

    @Override
    public void publishStaffDepartmentChanged(StaffDepartmentChangedEvent event) {
        publishAfterCommit(StaffDepartmentChangedEvent.ROUTING_KEY, event);
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
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EVENT_EXCHANGE,
                routingKey,
                payload);
    }
}
