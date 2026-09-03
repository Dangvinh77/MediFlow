package com.mediflow.pharmacy.infrastructure.messaging;

import com.mediflow.pharmacy.application.event.PrescriptionCreatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Kiểm tra routing key và thời điểm publish mà không cần RabbitMQ thật. */
class PharmacyEventPublisherAdapterTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final PharmacyEventPublisherAdapter adapter =
            new PharmacyEventPublisherAdapter(rabbitTemplate);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishPrescriptionCreated_withoutTransaction_sendsImmediatelyWithContractRoutingKey() {
        PrescriptionCreatedEvent event = event();

        adapter.publishPrescriptionCreated(event);

        verify(rabbitTemplate).convertAndSend(
                "mediflow.events",
                "prescription.created",
                event);
    }

    @Test
    void publishPrescriptionCreated_activeTransaction_sendsExactlyOnceAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        PrescriptionCreatedEvent event = event();

        adapter.publishPrescriptionCreated(event);

        verifyNoInteractions(rabbitTemplate);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(rabbitTemplate, times(1)).convertAndSend(
                "mediflow.events",
                "prescription.created",
                event);
    }

    @Test
    void publishPrescriptionCreated_rolledBackTransaction_sendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        adapter.publishPrescriptionCreated(event());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(rabbitTemplate);
    }

    private PrescriptionCreatedEvent event() {
        return new PrescriptionCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                "correlation-test",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("2000.00"),
                List.of(new PrescriptionCreatedEvent.Item(
                        UUID.randomUUID(),
                        "Paracetamol",
                        2,
                        new BigDecimal("1000.00"))));
    }
}
