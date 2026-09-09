package com.mediflow.lab.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.lab.application.event.LabRequestCreatedEvent;
import com.mediflow.lab.application.event.LabResultCreatedEvent;

class LabEventPublisherAdapterTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final LabEventPublisherAdapter adapter = new LabEventPublisherAdapter(rabbitTemplate);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishRequestCreated_withoutTransaction_sendsWithContractRoutingKey() {
        LabRequestCreatedEvent event = requestEvent();

        adapter.publishRequestCreated(event);

        verify(rabbitTemplate).convertAndSend(
                "mediflow.events", "lab.request.created", event);
    }

    @Test
    void publishResultCreated_withoutTransaction_sendsWithContractRoutingKey() {
        LabResultCreatedEvent event = resultEvent();

        adapter.publishResultCreated(event);

        verify(rabbitTemplate).convertAndSend(
                "mediflow.events", "lab.result.created", event);
    }

    @Test
    void publishRequestCreated_activeTransaction_sendsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        LabRequestCreatedEvent event = requestEvent();

        adapter.publishRequestCreated(event);

        verifyNoInteractions(rabbitTemplate);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(rabbitTemplate).convertAndSend(
                "mediflow.events", "lab.request.created", event);
    }

    @Test
    void publishResultCreated_rolledBackTransaction_sendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        adapter.publishResultCreated(resultEvent());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(rabbitTemplate);
    }

    private LabRequestCreatedEvent requestEvent() {
        return new LabRequestCreatedEvent(
                UUID.randomUUID(), Instant.now(), "correlation-test",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "HEMATOLOGY", LocalDate.now());
    }

    private LabResultCreatedEvent resultEvent() {
        return new LabResultCreatedEvent(
                UUID.randomUUID(), Instant.now(), "correlation-test",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "HEMATOLOGY", LocalDate.now(),
                List.of(new LabResultCreatedEvent.Result(
                        UUID.randomUUID(), "WBC", "7.2", "10^9/L", "4.0-10.0")),
                "Normal");
    }
}
