package com.mediflow.clinical.messaging.consumer;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;
import com.mediflow.clinical.messaging.consumer.payload.LabResultCreatedPayload;
import com.mediflow.clinical.messaging.consumer.payload.PrescriptionFilledPayload;

/** Sole listener for {@code clinical.q}; dispatches by the broker routing key. */
@Component
public class ClinicalQueueConsumer {

    private final ObjectMapper objectMapper;
    private final LabResultCreatedConsumer labResults;
    private final PrescriptionFilledConsumer prescriptions;

    public ClinicalQueueConsumer(ObjectMapper objectMapper, LabResultCreatedConsumer labResults,
                                 PrescriptionFilledConsumer prescriptions) {
        this.objectMapper = objectMapper;
        this.labResults = labResults;
        this.prescriptions = prescriptions;
    }

    @RabbitListener(queues = "${mediflow.clinical.rabbit.queue:clinical.q}")
    public void consume(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        try {
            switch (routingKey) {
                case RabbitConfig.LAB_RESULT_CREATED -> labResults.consume(
                        objectMapper.readValue(message.getBody(), LabResultCreatedPayload.class));
                case RabbitConfig.PRESCRIPTION_FILLED -> prescriptions.consume(
                        objectMapper.readValue(message.getBody(), PrescriptionFilledPayload.class));
                default -> throw new MessageConversionException(
                        "Unsupported clinical routing key: " + routingKey);
            }
        } catch (IOException exception) {
            throw new MessageConversionException(
                    "Invalid " + routingKey + " payload", exception);
        }
    }
}
