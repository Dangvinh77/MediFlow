package com.mediflow.surgery.infrastructure.messaging;

import com.mediflow.surgery.application.port.out.SurgeryEventPublisherPort;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Sends stored JSON bytes with publisher confirms and mandatory-return detection. */
public class RabbitSurgeryEventPublisherAdapter implements SurgeryEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final long confirmTimeoutMillis;

    public RabbitSurgeryEventPublisherAdapter(
            RabbitTemplate rabbitTemplate, String exchange, long confirmTimeoutMillis) {
        if (rabbitTemplate == null || exchange == null || exchange.isBlank()
                || confirmTimeoutMillis < 1 || confirmTimeoutMillis > 60_000) {
            throw new IllegalArgumentException("Surgery Rabbit publisher settings are invalid");
        }
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.confirmTimeoutMillis = confirmTimeoutMillis;
    }

    @Override
    public void publish(OutgoingMessage outgoing) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(outgoing.eventId().toString());
        properties.setCorrelationId(outgoing.correlationId());
        properties.setHeader("eventType", outgoing.eventType());
        properties.setHeader("eventVersion", outgoing.eventVersion());
        CorrelationData confirmation = new CorrelationData(outgoing.eventId() + ":" + java.util.UUID.randomUUID());

        try {
            rabbitTemplate.send(exchange, outgoing.eventType(),
                    new Message(outgoing.payload(), properties), confirmation);
            CorrelationData.Confirm result = confirmation.getFuture()
                    .get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);
            ReturnedMessage returned = confirmation.getReturned();
            if (returned != null) {
                throw new SurgeryEventPublishException(
                        "RabbitMQ returned an unroutable Surgery event", true);
            }
            if (!result.isAck()) {
                throw new SurgeryEventPublishException("RabbitMQ nacked the Surgery event", false);
            }
        } catch (TimeoutException exception) {
            throw new SurgeryEventPublishException("RabbitMQ confirm timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SurgeryEventPublishException("RabbitMQ confirm wait was interrupted", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new SurgeryEventPublishException("RabbitMQ confirm failed", exception.getCause());
        } catch (AmqpException exception) {
            throw new SurgeryEventPublishException("RabbitMQ publish failed", exception);
        }
    }
}
