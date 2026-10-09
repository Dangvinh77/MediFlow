package com.mediflow.notification.messaging.consumer;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.notification.application.port.in.ReactToSurgeryNoticeUseCase;
import com.mediflow.notification.application.port.out.SurgeryNoticeWirePort;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public final class SurgeryNoticeConsumer {
    private final SurgeryNoticeWirePort decoder;
    private final ReactToSurgeryNoticeUseCase notices;
    public SurgeryNoticeConsumer(SurgeryNoticeWirePort decoder, ReactToSurgeryNoticeUseCase notices) { this.decoder = decoder; this.notices = notices; }

    @RabbitListener(id = "notificationSurgeryNotices", queues = "notification.surgery-v1.q", containerFactory = "surgeryNoticeListenerFactory")
    public void consume(Message message) {
        try { notices.receive(decoder.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody())); }
        catch (IllegalArgumentException | BusinessRuleException | DuplicateResourceException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting Surgery notification contract");
        }
    }
}
