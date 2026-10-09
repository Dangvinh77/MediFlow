package com.mediflow.notification.messaging.consumer;

import com.mediflow.notification.application.port.in.ReactToSurgeryPaymentNoticeUseCase;
import com.mediflow.notification.application.port.out.SurgeryPaymentNoticeWirePort;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class SurgeryPaymentNoticeConsumer {
    private final SurgeryPaymentNoticeWirePort wire;
    private final ReactToSurgeryPaymentNoticeUseCase notices;
    public SurgeryPaymentNoticeConsumer(SurgeryPaymentNoticeWirePort wire, ReactToSurgeryPaymentNoticeUseCase notices) { this.wire = wire; this.notices = notices; }
    @RabbitListener(id = "notificationSurgeryPaymentRequests", queues = "notification.surgery-payment-requests-v1.q", containerFactory = "surgeryPaymentNoticeListenerFactory")
    public void receive(Message message) {
        try { notices.receive(wire.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody())); }
        catch (IllegalArgumentException | BusinessRuleException | DuplicateResourceException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting Surgery payment-request notice");
        }
    }
}
