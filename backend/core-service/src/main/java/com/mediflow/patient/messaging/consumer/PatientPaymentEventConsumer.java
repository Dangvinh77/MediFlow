package com.mediflow.patient.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.patient.infrastructure.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Compatibility consumer: payment.completed is intentionally log-only in Patient V1. */
@Component
public class PatientPaymentEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(PatientPaymentEventConsumer.class);
    private final ObjectMapper objectMapper;

    public PatientPaymentEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onPaymentCompleted(Message message) throws IOException {
        JsonNode payload = objectMapper.readTree(message.getBody());
        log.info("Received payment.completed for patientId={} eventId={} (log-only)",
                text(payload, "patientId"), text(payload, "eventId"));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "null" : value.asText();
    }
}
