package com.mediflow.notification.messaging.consumer;

import com.mediflow.notification.application.service.RegistrationNoticeService;
import com.mediflow.notification.domain.exception.NotificationEventConflictException;
import com.mediflow.notification.infrastructure.messaging.RegistrationNoticeDecoder;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.transaction.support.TransactionTemplate;

public class RegistrationNoticeConsumer {
    private final RegistrationNoticeDecoder decoder;
    private final RegistrationNoticeService service;
    private final TransactionTemplate transaction;

    public RegistrationNoticeConsumer(RegistrationNoticeDecoder decoder, RegistrationNoticeService service, TransactionTemplate transaction) {
        this.decoder = decoder; this.service = service; this.transaction = transaction;
    }

    @RabbitListener(queues = "notification.outpatient-registration.q", containerFactory = "notificationListenerFactory")
    public void onMessage(org.springframework.amqp.core.Message message) {
        receive(message.getBody());
    }

    public void receive(byte[] raw) {
        try {
            var command = decoder.decode(raw);
            transaction.executeWithoutResult(status -> service.receive(command));
        } catch (IllegalArgumentException | NotificationEventConflictException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting registration notice", invalid);
        }
    }
}
