package com.mediflow.lab.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mediflow.lab.application.event.LabRequestCreatedEvent;
import com.mediflow.lab.application.event.LabResultCreatedEvent;

class LabEventPublisherAdapterTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final LabEventPublisherAdapter adapter = new LabEventPublisherAdapter(rabbitTemplate);
    private final Connection connection = mock(Connection.class);
    private final PlatformTransactionManager transactionManager =
            new DataSourceTransactionManager(new SingleConnectionDataSource(connection, true));
    private final TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishRequestCreated_withoutTransaction_rejectsAndDoesNotSend() {
        LabRequestCreatedEvent event = requestEvent();

        assertThatThrownBy(() -> adapter.publishRequestCreated(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Lab event publication requires an active transaction");

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void publishResultCreated_withoutTransaction_rejectsAndDoesNotSend() {
        LabResultCreatedEvent event = resultEvent();

        assertThatThrownBy(() -> adapter.publishResultCreated(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Lab event publication requires an active transaction");

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void publishRequestCreated_synchronizationWithoutTransaction_rejectsAndDoesNotSend() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            LabRequestCreatedEvent event = requestEvent();

            assertThatThrownBy(() -> adapter.publishRequestCreated(event))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Lab event publication requires an active transaction");

            verifyNoInteractions(rabbitTemplate);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishRequestCreated_beforeCommit_doesNotSend_andCommitSendsOnce() {
        LabRequestCreatedEvent event = requestEvent();

        transaction.executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
            adapter.publishRequestCreated(event);

            verifyNoInteractions(rabbitTemplate);
        });

        verify(rabbitTemplate).convertAndSend(
                "mediflow.events", "lab.request.created", event);
    }

    @Test
    void publishResultCreated_beforeCommit_doesNotSend_andCommitSendsOnce() {
        LabResultCreatedEvent event = resultEvent();

        transaction.executeWithoutResult(status -> {
            adapter.publishResultCreated(event);

            verifyNoInteractions(rabbitTemplate);
        });

        verify(rabbitTemplate).convertAndSend(
                "mediflow.events", "lab.result.created", event);
    }

    @Test
    void publishResultCreated_rolledBackTransaction_sendsNothing() {
        LabResultCreatedEvent event = resultEvent();

        transaction.executeWithoutResult(status -> {
            adapter.publishResultCreated(event);
            status.setRollbackOnly();

            verify(rabbitTemplate, never()).convertAndSend(
                    "mediflow.events", "lab.result.created", event);
        });

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
