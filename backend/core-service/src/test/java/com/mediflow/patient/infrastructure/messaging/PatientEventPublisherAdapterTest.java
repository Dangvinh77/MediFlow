package com.mediflow.patient.infrastructure.messaging;

import com.mediflow.patient.application.event.PatientCreatedEvent;
import com.mediflow.patient.infrastructure.config.RabbitConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

class PatientEventPublisherAdapterTest {
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final PatientEventPublisherAdapter adapter = new PatientEventPublisherAdapter(rabbitTemplate);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createdEventIsSentOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        PatientCreatedEvent event = new PatientCreatedEvent(UUID.randomUUID(), Instant.now(), "corr",
                UUID.randomUUID(), "Nguyen Van A", "a@example.com", "0900000000");

        adapter.publishCreated(event);

        verifyNoInteractions(rabbitTemplate);
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(rabbitTemplate).convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_PATIENT_CREATED, event);
    }
}
