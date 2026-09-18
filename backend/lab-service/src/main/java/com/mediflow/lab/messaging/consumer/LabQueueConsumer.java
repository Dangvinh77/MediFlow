package com.mediflow.lab.messaging.consumer;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.infrastructure.config.RabbitConfig;
import com.mediflow.lab.messaging.consumer.payload.MedicalRecordCreatedPayload;
import com.mediflow.lab.messaging.consumer.payload.PaymentCompletedPayload;

/** Sole listener for {@code lab.q}; dispatches by the broker routing key. */
@Component
public class LabQueueConsumer {

    private final ObjectMapper objectMapper;
    private final MedicalRecordCreatedConsumer medicalRecords;
    private final PaymentCompletedConsumer payments;

    public LabQueueConsumer(ObjectMapper objectMapper, MedicalRecordCreatedConsumer medicalRecords,
                            PaymentCompletedConsumer payments) {
        this.objectMapper = objectMapper;
        this.medicalRecords = medicalRecords;
        this.payments = payments;
    }

    @RabbitListener(queues = "${mediflow.lab.rabbit.queue:lab.q}")
    public void consume(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        try {
            switch (routingKey) {
                case RabbitConfig.MEDICAL_RECORD_CREATED -> medicalRecords.consume(
                        objectMapper.readValue(message.getBody(), MedicalRecordCreatedPayload.class));
                case RabbitConfig.PAYMENT_COMPLETED -> payments.consume(
                        objectMapper.readValue(message.getBody(), PaymentCompletedPayload.class));
                default -> throw new MessageConversionException(
                        "Unsupported lab routing key: " + routingKey);
            }
        } catch (IOException exception) {
            throw new MessageConversionException("Invalid " + routingKey + " payload", exception);
        }
    }
}
