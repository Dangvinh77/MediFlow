package com.mediflow.notification.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.notification.application.event.NotificationSentEvent;
import com.mediflow.notification.application.port.out.NotificationEventPublisherPort;
import com.mediflow.notification.infrastructure.config.RabbitConfig;

/**
 * Hiện thực {@link NotificationEventPublisherPort} — publish {@code notification.sent} lên
 * {@value RabbitConfig#EXCHANGE} sau khi transaction commit (backend-spec/07-notification.md §10,
 * docs/ai/06-events-rabbitmq.md, docs/ai/reference/EventPublisherAdapter.java).
 */
@Component
public class NotificationEventPublisherAdapter implements NotificationEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventPublisherAdapter.class);

    private final RabbitTemplate rabbitTemplate;

    public NotificationEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishSent(NotificationSentEvent e) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(e);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(e);
            }
        });
    }

    private void send(NotificationSentEvent e) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_NOTIFICATION_SENT, e);
        log.info("Published {} for notificationId={}", RabbitConfig.RK_NOTIFICATION_SENT, e.notificationId());
    }
}
