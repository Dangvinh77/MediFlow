package com.mediflow.<service>.infrastructure.messaging;

import com.mediflow.<service>.application.port.out.XxxEventPublisherPort;
import com.mediflow.<service>.infrastructure.config.RabbitConfig;
import com.mediflow.<service>.infrastructure.messaging.payload.XxxCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes domain events to the shared topic exchange.
 *
 * <p><strong>Sends after the transaction commits.</strong> The application service calls this
 * inside its {@code @Transactional} method, but the actual send is deferred through a
 * {@link TransactionSynchronization}. Publishing inline would announce a change that a later
 * rollback erases — and consumers cannot un-see an event. Critical for the billing/pharmacy saga,
 * where a {@code payment.completed} sent before commit could dispense drugs for a payment that then
 * rolls back (docs/ai/06-events-rabbitmq.md).
 *
 * <p>Keeping this here, rather than injecting Spring's RabbitTemplate into the application layer,
 * is what lets that layer stay free of Spring beyond {@code @Service}/{@code @Transactional}.
 */
@Component
public class XxxEventPublisherAdapter implements XxxEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(XxxEventPublisherAdapter.class);

    private final RabbitTemplate rabbitTemplate;

    public XxxEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishCreated(UUID id, String hoTen) {
        XxxCreatedEvent event = new XxxCreatedEvent(
                UUID.randomUUID(), Instant.now(), null,
                id, hoTen);

        guiSauKhiCommit(RabbitConfig.RK_SOME_EVENT, event, id);
    }

    private void guiSauKhiCommit(String routingKey, Object event, UUID id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // No transaction in progress (a test, or a direct call) — send immediately.
            gui(routingKey, event, id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                gui(routingKey, event, id);
            }
        });
    }

    private void gui(String routingKey, Object event, UUID id) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, event);
        // id is an opaque UUID, safe to log. Business fields (names, phone, amounts) are not.
        log.info("Published {} for id={}", routingKey, id);
    }
}
