package com.mediflow.patient.infrastructure.messaging;

import com.mediflow.patient.infrastructure.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** At-least-once delivery: a crash after confirm leaves the original event available for replay. */
public class PatientOutboxDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(PatientOutboxDispatcher.class);
    private final PatientOutboxStore store;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;
    private final int maximumAttempts;

    public PatientOutboxDispatcher(PatientOutboxStore store, RabbitTemplate rabbit,
                                   TransactionTemplate transactions, int maximumAttempts) {
        if (maximumAttempts < 1) throw new IllegalArgumentException("maximumAttempts must be positive");
        this.store = store;
        this.rabbit = rabbit;
        this.transactions = transactions;
        this.maximumAttempts = maximumAttempts;
    }

    public void dispatch() {
        // A row lock fences competing workers until the confirm and status update commit.
        for (int index = 0; index < 20; index++) {
            try {
                if (!Boolean.TRUE.equals(transactions.execute(status -> sendNext()))) return;
            } catch (RuntimeException failure) {
                LOG.warn("Patient outbox transaction deferred: {}", failure.getClass().getSimpleName());
                return;
            }
        }
    }

    private boolean sendNext() {
        var pending = store.lockNext();
        if (pending.isEmpty()) return false;
        var delivery = pending.get();
        try {
            publishConfirmed(delivery);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Patient publisher confirm interrupted", interrupted);
        } catch (Exception failure) {
            // Retain only a redacted reason, never broker exception text or patient data.
            store.markFailed(delivery, maximumAttempts, failure.getClass().getSimpleName());
            LOG.warn("Patient outbox retry eventId={} attempt={}", delivery.id(), delivery.attempts() + 1);
            return true;
        }
        // Outside the publish catch: a mark/commit failure must roll back the claim.
        store.markPublished(delivery.id());
        return true;
    }

    private void publishConfirmed(PatientOutboxStore.Delivery delivery) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(delivery.id().toString());
        properties.setCorrelationId(delivery.correlationId());
        properties.setHeader("__TypeId__", delivery.routingKey().equals(RabbitConfig.RK_PATIENT_CREATED)
                ? "com.mediflow.patient.application.event.PatientCreatedEvent"
                : delivery.routingKey().equals(RabbitConfig.RK_PATIENT_ARCHIVED)
                    ? "com.mediflow.patient.application.event.PatientArchivedEvent"
                    : "com.mediflow.patient.application.event.PatientUpdatedEvent");
        var confirmation = new CorrelationData(delivery.id() + ":" + UUID.randomUUID());
        rabbit.send(RabbitConfig.EXCHANGE, delivery.routingKey(),
                new Message(delivery.payload().getBytes(StandardCharsets.UTF_8), properties), confirmation);
        var result = confirmation.getFuture().get(5, TimeUnit.SECONDS);
        if (!result.isAck() || confirmation.getReturned() != null) {
            throw new IllegalStateException("Patient event not confirmed or unroutable");
        }
    }
}
