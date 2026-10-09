package com.mediflow.notification.messaging.consumer;

import com.mediflow.notification.application.port.in.ReactToRefundNoticeUseCase;
import com.mediflow.notification.application.port.out.RefundNoticeWirePort;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class RefundNoticeConsumer {
    private final RefundNoticeWirePort wire;
    private final ReactToRefundNoticeUseCase notices;
    public RefundNoticeConsumer(RefundNoticeWirePort wire, ReactToRefundNoticeUseCase notices) { this.wire = wire; this.notices = notices; }
    @RabbitListener(id = "notificationRefundNotices", queues = "notification.refunds-v1.q", containerFactory = "refundNoticeListenerFactory")
    public void receive(Message message) {
        try { notices.receive(wire.decode(message.getMessageProperties().getReceivedRoutingKey(), message.getBody())); }
        catch (IllegalArgumentException | BusinessRuleException | DuplicateResourceException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid or conflicting completed-refund notice");
        }
    }
}
