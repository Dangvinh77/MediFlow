package com.mediflow.clinical.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;

class ClinicalQueueConsumerTest {

    private final LabResultCreatedConsumer labResults = mock(LabResultCreatedConsumer.class);
    private final PrescriptionFilledConsumer prescriptions = mock(PrescriptionFilledConsumer.class);
    private final ClinicalQueueConsumer consumer = new ClinicalQueueConsumer(
            new ObjectMapper().findAndRegisterModules(), labResults, prescriptions);

    @Test
    void consume_v1LabEnvelope_dispatchesCanonicalNestedProjection() throws IOException {
        consumer.consume(message(RabbitConfig.LAB_RESULT_CREATED,
                readFixture("lab.result.created.v1.json")));

        verify(labResults).consume(argThat(payload ->
                UUID.fromString("00000000-0000-4000-8000-000000000011").equals(payload.eventId())
                        && UUID.fromString("00000000-0000-4000-8000-000000000004").equals(payload.recordId())
                        && UUID.fromString("00000000-0000-4000-8000-000000000002").equals(payload.labId())
                        && "Normal".equals(payload.conclusion())
                        && "00000000-0000-4000-8000-000000000007".equals(payload.correlationId())));
    }

    @Test
    void consume_v1LabEnvelope_unknownVersion_rejects() throws IOException {
        String fixture = readFixture("lab.result.created.v1.json")
                .replace("\"version\": 1", "\"version\": 2");

        assertThatThrownBy(() -> consumer.consume(message(RabbitConfig.LAB_RESULT_CREATED, fixture)))
                .isInstanceOf(MessageConversionException.class)
                .hasMessageContaining("version");
        verifyNoInteractions(labResults);
    }

    @Test
    void consume_v1LabEnvelope_wrongEventType_rejects() throws IOException {
        String fixture = readFixture("lab.result.created.v1.json")
                .replace("\"eventType\": \"lab.result.created\"",
                        "\"eventType\": \"lab.request.created\"");

        assertThatThrownBy(() -> consumer.consume(message(RabbitConfig.LAB_RESULT_CREATED, fixture)))
                .isInstanceOf(MessageConversionException.class)
                .hasMessageContaining("eventType");
        verifyNoInteractions(labResults);
    }

    @Test
    void consume_v1LabEnvelope_wrongProducer_rejects() throws IOException {
        String fixture = readFixture("lab.result.created.v1.json")
                .replace("\"producer\": \"lab-service\"", "\"producer\": \"billing-service\"");

        assertThatThrownBy(() -> consumer.consume(message(RabbitConfig.LAB_RESULT_CREATED, fixture)))
                .isInstanceOf(MessageConversionException.class)
                .hasMessageContaining("producer");
        verifyNoInteractions(labResults);
    }

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

    private static String readFixture(String name) throws IOException {
        try (var input = ClinicalQueueConsumerTest.class.getResourceAsStream("/contracts/" + name)) {
            assertThat(input).as("canonical %s fixture", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
