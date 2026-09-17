package com.mediflow.report.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

/**
 * Rejects poison messages after bounded retry without logging their body.
 * Event payloads can contain clinical or billing data, so only routing metadata is retained.
 */
public final class ReportMessageRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(ReportMessageRecoverer.class);

    @Override
    public void recover(Message message, Throwable cause) {
        var properties = message == null ? null : message.getMessageProperties();
        String routingKey = properties == null ? null : properties.getReceivedRoutingKey();
        String messageId = properties == null ? null : properties.getMessageId();
        String correlationId = properties == null ? null : properties.getCorrelationId();
        log.warn("report event rejected after retries routingKey={} messageId={} correlationId={}",
                routingKey, messageId, correlationId);
        throw new AmqpRejectAndDontRequeueException("Report event rejected after retries");
    }
}
