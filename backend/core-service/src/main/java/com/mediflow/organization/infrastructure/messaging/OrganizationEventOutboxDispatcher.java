package com.mediflow.organization.infrastructure.messaging;

import com.mediflow.organization.infrastructure.config.RabbitMqConfig;
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

/** At-least-once relay; an uncommitted confirm can replay the exact stored event and id. */
public class OrganizationEventOutboxDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationEventOutboxDispatcher.class);
    private final OrganizationEventOutboxStore store;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;
    private final int maximumAttempts;

    public OrganizationEventOutboxDispatcher(OrganizationEventOutboxStore store, RabbitTemplate rabbit,
                                               TransactionTemplate transactions, int maximumAttempts) {
        if (maximumAttempts < 1) throw new IllegalArgumentException("maximumAttempts must be positive");
        this.store = store;
        this.rabbit = rabbit;
        this.transactions = transactions;
        this.maximumAttempts = maximumAttempts;
    }

    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            try {
                if (!Boolean.TRUE.equals(transactions.execute(status -> sendNext()))) return;
            } catch (RuntimeException failure) {
                LOG.warn("Organization event outbox deferred: {}", failure.getClass().getSimpleName());
                return;
            }
        }
    }

    private boolean sendNext() {
        var pending = store.lockNext();
        if (pending.isEmpty()) return false;
        var delivery = pending.get();
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(delivery.id().toString());
            properties.setCorrelationId(delivery.correlationId());
            properties.setHeader("__TypeId__", delivery.typeId());
            CorrelationData confirm = new CorrelationData(delivery.id() + ":" + UUID.randomUUID());
            rabbit.send(RabbitMqConfig.EVENT_EXCHANGE, delivery.routingKey(),
                    new Message(delivery.payload().getBytes(StandardCharsets.UTF_8), properties), confirm);
            var result = confirm.getFuture().get(5, TimeUnit.SECONDS);
            if (!result.isAck() || confirm.getReturned() != null)
                throw new IllegalStateException("Organization event not confirmed or unroutable");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            store.markFailed(delivery, maximumAttempts, "InterruptedException");
            LOG.warn("Organization event outbox retry eventId={} attempt={}", delivery.id(), delivery.attempts() + 1);
            return true;
        } catch (Exception failure) {
            store.markFailed(delivery, maximumAttempts, failure.getClass().getSimpleName());
            LOG.warn("Organization event outbox retry eventId={} attempt={}", delivery.id(), delivery.attempts() + 1);
            return true;
        }
        // Keep failures here outside the broker catch so the transaction rolls back and remains retryable.
        store.markPublished(delivery.id());
        return true;
    }
}
