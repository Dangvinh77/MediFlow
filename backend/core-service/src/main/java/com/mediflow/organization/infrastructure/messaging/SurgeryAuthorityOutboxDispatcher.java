package com.mediflow.organization.infrastructure.messaging;

import com.mediflow.organization.infrastructure.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Bounded at-least-once delivery; commit-after-confirm failure deliberately permits duplicates. */
public class SurgeryAuthorityOutboxDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(SurgeryAuthorityOutboxDispatcher.class);
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;

    public SurgeryAuthorityOutboxDispatcher(JdbcTemplate jdbc, RabbitTemplate rabbit, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${mediflow.organization.surgery-authority.outbox-delay-ms:5000}")
    public void dispatch() {
        for (int index = 0; index < 20; index++) {
            try {
                Boolean sent = transactions.execute(status -> sendNext());
                if (!Boolean.TRUE.equals(sent)) return;
            } catch (RuntimeException failure) {
                LOG.warn("Organization surgery authority outbox delivery deferred: {}",
                        failure.getClass().getSimpleName());
                return;
            }
        }
    }

    private boolean sendNext() {
        var pending = jdbc.query("""
                SELECT event_id,routing_key,payload::text AS payload FROM surgery_authority_outbox
                WHERE published_at IS NULL ORDER BY occurred_at,event_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (row, index) -> new Delivery(row.getObject("event_id", UUID.class),
                row.getString("routing_key"), row.getString("payload")));
        if (pending.isEmpty()) return false;
        Delivery delivery = pending.getFirst();
        CorrelationData confirmation = new CorrelationData(delivery.id() + ":" + UUID.randomUUID());
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(delivery.id().toString());
        try {
            rabbit.send(RabbitMqConfig.EVENT_EXCHANGE, delivery.routingKey(),
                    new Message(delivery.payload().getBytes(StandardCharsets.UTF_8), properties), confirmation);
            var result = confirmation.getFuture().get(5, TimeUnit.SECONDS);
            if (!result.isAck() || confirmation.getReturned() != null) {
                throw new IllegalStateException("Authority event was not confirmed/routed");
            }
            jdbc.update("UPDATE surgery_authority_outbox SET published_at=now() WHERE event_id=?",
                    delivery.id());
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Authority confirm interrupted", interrupted);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new IllegalStateException("Authority publisher confirm failed", failure);
        }
    }

    private record Delivery(UUID id, String routingKey, String payload) {}
}
