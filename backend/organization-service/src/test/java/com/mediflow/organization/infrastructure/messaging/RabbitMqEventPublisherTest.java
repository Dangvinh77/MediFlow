package com.mediflow.organization.infrastructure.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.organization.application.event.StaffCreatedEvent;
import com.mediflow.organization.infrastructure.config.RabbitMqConfig;

@ExtendWith(MockitoExtension.class)
class RabbitMqEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publish_withoutTransaction_publishesImmediately() {
        RabbitMqEventPublisher publisher = new RabbitMqEventPublisher(rabbitTemplate);
        StaffCreatedEvent event = event();

        publisher.publishStaffCreated(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMqConfig.EVENT_EXCHANGE, StaffCreatedEvent.ROUTING_KEY, event);
    }

    @Test
    void publish_withTransaction_waitsUntilAfterCommit() {
        RabbitMqEventPublisher publisher = new RabbitMqEventPublisher(rabbitTemplate);
        StaffCreatedEvent event = event();
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishStaffCreated(event);
        verifyNoInteractions(rabbitTemplate);

        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();

        verify(rabbitTemplate).convertAndSend(
                RabbitMqConfig.EVENT_EXCHANGE, StaffCreatedEvent.ROUTING_KEY, event);
    }

    private StaffCreatedEvent event() {
        return new StaffCreatedEvent(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID().toString(),
                UUID.randomUUID(), "Alex Morgan", UUID.randomUUID(), "NURSE");
    }
}
