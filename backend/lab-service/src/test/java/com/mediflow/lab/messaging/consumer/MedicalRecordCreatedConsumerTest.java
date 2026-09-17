package com.mediflow.lab.messaging.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;

import com.mediflow.lab.application.dto.command.MedicalRecordCreatedCommand;
import com.mediflow.lab.application.port.in.ReactToMedicalRecordUseCase;
import com.mediflow.lab.infrastructure.config.RabbitConfig;
import com.mediflow.lab.messaging.consumer.payload.MedicalRecordCreatedPayload;

class MedicalRecordCreatedConsumerTest {

    private final ReactToMedicalRecordUseCase useCase = mock(ReactToMedicalRecordUseCase.class);
    private final MedicalRecordCreatedConsumer consumer = new MedicalRecordCreatedConsumer(useCase);
    private final Jackson2JsonMessageConverter converter = new RabbitConfig().rabbitJsonMessageConverter();

    @Test
    void consume_mapsCanonicalClinicalIdentifiers() {
        UUID eventId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        MedicalRecordCreatedPayload payload = new MedicalRecordCreatedPayload(
                eventId, Instant.now(), "correlation-test", recordId, patientId,
                UUID.randomUUID(), departmentId, "Routine examination", LocalDate.now());

        consumer.consume(payload);

        verify(useCase).onMedicalRecordCreated(
                new MedicalRecordCreatedCommand(eventId, recordId, patientId, departmentId));
    }

    @Test
    void consume_convertsClinicalJsonEnvelopeAndIgnoresAdditiveFields() {
        UUID eventId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID recordId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        UUID patientId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        UUID departmentId = UUID.fromString("40000000-0000-0000-0000-000000000004");

        MedicalRecordCreatedPayload payload = convert(clinicalEventJson(
                eventId, recordId, patientId, departmentId, null));

        consumer.consume(payload);

        verify(useCase).onMedicalRecordCreated(
                new MedicalRecordCreatedCommand(eventId, recordId, patientId, departmentId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventId", "recordId", "patientId", "departmentId"})
    void consume_missingRequiredIdentifier_rejectsBeforeUseCase(String missingField) {
        MedicalRecordCreatedPayload payload = convert(clinicalEventJson(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                UUID.fromString("40000000-0000-0000-0000-000000000004"),
                missingField));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missingField);

        verifyNoInteractions(useCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventId", "recordId", "patientId", "departmentId"})
    void converter_malformedRequiredIdentifier_rejectsBeforeConsumer(String malformedField) {
        UUID eventId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID recordId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        UUID patientId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        UUID departmentId = UUID.fromString("40000000-0000-0000-0000-000000000004");
        String validValue = switch (malformedField) {
            case "eventId" -> eventId.toString();
            case "recordId" -> recordId.toString();
            case "patientId" -> patientId.toString();
            case "departmentId" -> departmentId.toString();
            default -> throw new IllegalArgumentException("Unexpected field: " + malformedField);
        };
        String json = clinicalEventJson(eventId, recordId, patientId, departmentId, null)
                .replace("\"" + malformedField + "\": \"" + validValue + "\"",
                        "\"" + malformedField + "\": \"not-a-uuid\"");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> convert(json))
                .isInstanceOf(MessageConversionException.class);

        verifyNoInteractions(useCase);
    }

    private MedicalRecordCreatedPayload convert(String json) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        message.getMessageProperties().setInferredArgumentType(MedicalRecordCreatedPayload.class);
        message.getMessageProperties().setHeader(
                "__TypeId__", "com.mediflow.clinical.application.event.MedicalRecordCreatedEvent");
        return (MedicalRecordCreatedPayload) converter.fromMessage(message, MedicalRecordCreatedPayload.class);
    }

    private String clinicalEventJson(UUID eventId, UUID recordId, UUID patientId, UUID departmentId,
                                     String missingField) {
        String json = """
                {
                  "eventId": "%s",
                  "occurredAt": "2026-09-16T02:00:00Z",
                  "correlationId": "corr-clinical-lab",
                  "recordId": "%s",
                  "patientId": "%s",
                  "doctorId": "50000000-0000-0000-0000-000000000005",
                  "departmentId": "%s",
                  "diagnosis": "Flu; Cough",
                  "examinationDate": "2026-09-16",
                  "clinicalExtension": "ignored by the Lab projection"
                }
                """.formatted(eventId, recordId, patientId, departmentId);
        if (missingField == null) {
            return json;
        }
        return json.replaceFirst("\\n\\s+\\\"" + missingField + "\\\": \\\"[^\\\"]*\\\",", "");
    }
}
