package com.mediflow.notification.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.notification.application.event.NotificationSentEvent;
import com.mediflow.notification.domain.model.NotificationChannel;
import com.mediflow.notification.domain.model.NotificationStatus;
import com.mediflow.notification.infrastructure.config.RabbitConfig;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Xác nhận publish sau commit khi có transaction, gửi ngay khi không có (docs/ai/06-events-rabbitmq.md). */
class NotificationEventPublisherAdapterTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final NotificationEventPublisherAdapter adapter = new NotificationEventPublisherAdapter(rabbitTemplate);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishSent_withoutActiveTransaction_sendsImmediately() {
        NotificationSentEvent event = new NotificationSentEvent(
                UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                NotificationChannel.IN_APP, NotificationStatus.SENT);

        adapter.publishSent(event);

        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_NOTIFICATION_SENT), eq(event));
    }

    @Test
    void publishSent_withActiveTransaction_deferredUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            NotificationSentEvent event = new NotificationSentEvent(
                    UUID.randomUUID(), Instant.now(), "cid", UUID.randomUUID(), UUID.randomUUID(),
                    NotificationChannel.EMAIL, NotificationStatus.FAILED);

            adapter.publishSent(event);

            verify(rabbitTemplate, never()).convertAndSend(
                    eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_NOTIFICATION_SENT), eq(event));

            for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_NOTIFICATION_SENT), eq(event));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
