package com.mediflow.clinical.messaging.consumer;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;

class ClinicalQueueConsumerTest {

    private final LabResultCreatedConsumer labResults = mock(LabResultCreatedConsumer.class);
    private final PrescriptionFilledConsumer prescriptions = mock(PrescriptionFilledConsumer.class);
    private final ClinicalQueueConsumer consumer = new ClinicalQueueConsumer(
            new ObjectMapper().findAndRegisterModules(), labResults, prescriptions);

    @Test
    void consume_labRoute_dispatchesLabProjection() {
        UUID eventId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();

        consumer.consume(message(RabbitConfig.LAB_RESULT_CREATED, """
                {"eventId":"%s","recordId":"%s","labId":"%s"}
                """.formatted(eventId, recordId, labId)));

        verify(labResults).consume(argThat(payload ->
                eventId.equals(payload.eventId())
                        && recordId.equals(payload.recordId())
                        && labId.equals(payload.labId())));
    }

    @Test
    void consume_prescriptionRoute_dispatchesPrescriptionProjection() {
        UUID eventId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();

        consumer.consume(message(RabbitConfig.PRESCRIPTION_FILLED, """
                {"eventId":"%s","recordId":"%s","prescriptionId":"%s","extra":"ignored"}
                """.formatted(eventId, recordId, prescriptionId)));

        verify(prescriptions).consume(argThat(payload ->
                eventId.equals(payload.eventId())
                        && recordId.equals(payload.recordId())
                        && prescriptionId.equals(payload.prescriptionId())));
    }

    private static Message message(String routingKey, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }
}
