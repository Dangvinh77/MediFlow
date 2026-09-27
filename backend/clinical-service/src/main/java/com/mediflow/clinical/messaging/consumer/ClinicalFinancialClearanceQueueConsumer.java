package com.mediflow.clinical.messaging.consumer;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.infrastructure.config.ClinicalClearanceRabbitConfig;
import com.mediflow.clinical.messaging.consumer.payload.FinancialClearanceEvent;

@Component
@ConditionalOnProperty(prefix = "mediflow.features.care-finance-v2", name = "enabled", havingValue = "true")
public class ClinicalFinancialClearanceQueueConsumer {
    private final ObjectMapper objectMapper;
    private final FinancialClearanceConsumer consumer;

    public ClinicalFinancialClearanceQueueConsumer(ObjectMapper objectMapper,
                                                   FinancialClearanceConsumer consumer) {
        this.objectMapper = objectMapper;
        this.consumer = consumer;
    }

    @RabbitListener(queues = ClinicalClearanceRabbitConfig.CLEARANCE_QUEUE, containerFactory = "clinicalClearanceListenerContainerFactory")
    public void consume(Message message) {
        try {
            consumer.consume(objectMapper.readValue(message.getBody(), FinancialClearanceEvent.class));
        } catch (IOException exception) {
            throw new MessageConversionException("Invalid financial.clearance.granted payload", exception);
        }
    }
}
