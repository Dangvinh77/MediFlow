package com.mediflow.lab.messaging.consumer;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.infrastructure.config.RabbitConfig;

class LabQueueConsumerTest {

    private final MedicalRecordCreatedConsumer medicalRecords = mock(MedicalRecordCreatedConsumer.class);
    private final PaymentCompletedConsumer payments = mock(PaymentCompletedConsumer.class);
    private final LabQueueConsumer consumer = new LabQueueConsumer(
            new ObjectMapper().findAndRegisterModules(), medicalRecords, payments);

    @Test
    void consume_medicalRecordRoute_dispatchesClinicalProjection() {
        UUID eventId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        consumer.consume(message(RabbitConfig.MEDICAL_RECORD_CREATED, """
                {"eventId":"%s","recordId":"%s","patientId":"%s","departmentId":"%s"}
                """.formatted(eventId, recordId, patientId, departmentId)));

        verify(medicalRecords).consume(argThat(payload ->
                eventId.equals(payload.eventId())
                        && recordId.equals(payload.recordId())
                        && patientId.equals(payload.patientId())
                        && departmentId.equals(payload.departmentId())));
    }

    @Test
    void consume_paymentRoute_dispatchesBillingProjection() {
        UUID eventId = UUID.randomUUID();
        UUID labTestId = UUID.randomUUID();

        consumer.consume(message(RabbitConfig.PAYMENT_COMPLETED, """
                {"eventId":"%s","labTestIds":["%s"],"invoiceId":"%s"}
                """.formatted(eventId, labTestId, UUID.randomUUID())));

        verify(payments).consume(argThat(payload ->
                eventId.equals(payload.eventId())
                        && payload.labTestIds().equals(java.util.List.of(labTestId))));
    }

    private static Message message(String routingKey, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }
}
