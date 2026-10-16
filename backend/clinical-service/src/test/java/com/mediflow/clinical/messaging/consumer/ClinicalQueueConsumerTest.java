package com.mediflow.clinical.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;
import com.mediflow.clinical.application.port.out.ConsumerInboxPort.InboxOutcome;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.service.ConsumerInboxBoundary;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;
import com.mediflow.clinical.messaging.consumer.inbox.InboxEvidenceFactory;

class ClinicalQueueConsumerTest {

    @ParameterizedTest
    @ValueSource(strings = {"version", "producer", "eventType", "sourceId", "careContext",
            "careEpisodeType", "dispenseId", "filledAt", "patientId", "departmentId", "careEpisodeId",
            "admissionId"})
    void malformedPrescriptionV1CannotClaimInboxOrAttach(String field) throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    "../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.outpatient.v1.json")));
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("payload");
        switch (field) {
            case "version" -> root.put("version", "1");
            case "producer", "eventType" -> root.put(field, "wrong");
            case "sourceId", "admissionId" -> payload.put(field, UUID.randomUUID().toString());
            case "careContext", "careEpisodeType" -> payload.put(field, "INPATIENT");
            default -> payload.putNull(field);
        }
        assertThatThrownBy(() -> consumer(true).consume(message(RabbitConfig.PRESCRIPTION_FILLED,
                objectMapper.writeValueAsString(root)))).isInstanceOf(MessageConversionException.class);
        verifyNoInteractions(inbox, prescriptions);
    }

    @Test
    void prescriptionOperationUsesDispenseIdentityRatherThanRecordIdentity() throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    "../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.outpatient.v1.json")));
        UUID dispenseId = UUID.randomUUID();
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("payload")).put("dispenseId", dispenseId.toString());
        consumer(true).consume(message(RabbitConfig.PRESCRIPTION_FILLED, objectMapper.writeValueAsString(root)));
        verify(inbox).execute(argThat(claim -> claim.operationKey().equals("clinical:prescription:v1:" + dispenseId)),
                any(Runnable.class));
    }

    @Test
    void outpatientMvp_acceptsOriginalPharmacyFilledBytesWithDispenseOperation() throws IOException {
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.outpatient.v1.json"));
        String raw = new String(bytes, StandardCharsets.UTF_8);
        consumer(true).consume(message(RabbitConfig.PRESCRIPTION_FILLED, raw));
        verify(prescriptions).consume(argThat(payload ->
                UUID.fromString("10000000-0000-4000-8000-000000000005").equals(payload.recordId())
                        && UUID.fromString("10000000-0000-4000-8000-000000000001").equals(payload.prescriptionId())));
        ArgumentCaptor<ConsumerInboxClaim> claim = ArgumentCaptor.forClass(ConsumerInboxClaim.class);
        verify(inbox).execute(claim.capture(), any(Runnable.class));
        assertThat(claim.getValue().operationKey())
                .isEqualTo("clinical:prescription:v1:10000000-0000-4000-8000-000000000005");
        assertThat(claim.getValue().rawBody()).isEqualTo(bytes);
    }

    private final LabResultCreatedConsumer labResults = mock(LabResultCreatedConsumer.class);
    private final PrescriptionFilledConsumer prescriptions = mock(PrescriptionFilledConsumer.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ConsumerInboxBoundary inbox = mock(ConsumerInboxBoundary.class);
    private final InboxEvidenceFactory evidenceFactory = new InboxEvidenceFactory(objectMapper);
    private final ClinicalQueueConsumer consumer = consumer(false);

    ClinicalQueueConsumerTest() {
        when(inbox.execute(any(ConsumerInboxClaim.class), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return InboxOutcome.APPLIED;
        });
    }

    @Test
    void consume_v1LabEnvelope_dispatchesCanonicalNestedProjection() throws IOException {
        String raw = readFixture("lab.result.created.v1.json");
        consumer.consume(message(RabbitConfig.LAB_RESULT_CREATED, raw));

        verify(labResults).consume(argThat(payload ->
                UUID.fromString("00000000-0000-4000-8000-000000000011").equals(payload.eventId())
                        && UUID.fromString("00000000-0000-4000-8000-000000000004").equals(payload.recordId())
                        && UUID.fromString("00000000-0000-4000-8000-000000000002").equals(payload.labId())
                        && "Normal".equals(payload.conclusion())
                        && "00000000-0000-4000-8000-000000000007".equals(payload.correlationId())));

        ArgumentCaptor<ConsumerInboxClaim> claim = ArgumentCaptor.forClass(ConsumerInboxClaim.class);
        verify(inbox).execute(claim.capture(), any(Runnable.class));
        assertThat(claim.getValue().operationKey())
                .isEqualTo("clinical:lab-result:v1:00000000-0000-4000-8000-000000000002:1");
        assertThat(claim.getValue().rawBody()).containsExactly(raw.getBytes(StandardCharsets.UTF_8));
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
    void consume_flatLabPayloadCannotSpoofV1ProjectionFields() {
        UUID eventId = UUID.randomUUID();
        String spoof = """
                {"eventId":"%s","recordId":"%s","labId":"%s","patientId":"%s",
                 "departmentId":"%s","sourceVersion":1,"careEpisodeType":"OUTPATIENT_VISIT",
                 "careEpisodeId":"%s","resultVersion":1}
                """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> consumer.consume(message(RabbitConfig.LAB_RESULT_CREATED, spoof)))
                .isInstanceOf(MessageConversionException.class)
                .hasMessageContaining("flat compatibility");
        verifyNoInteractions(labResults, inbox);
    }

    @Test
    void consume_incompleteFlatEnvelopeCannotFallBackToV0() {
        UUID eventId = UUID.randomUUID();
        String spoof = """
                {"eventId":"%s","eventType":"lab.result.created","version":1,
                 "producer":"lab-service","recordId":"%s","labId":"%s"}
                """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> consumer.consume(message(RabbitConfig.LAB_RESULT_CREATED, spoof)))
                .isInstanceOf(MessageConversionException.class);
        verifyNoInteractions(labResults, inbox);
    }

    @Test
    void outpatientMvp_quarantinesFlatV0LabResultBeforeReceiptAndEffect() {
        UUID eventId = UUID.randomUUID();
        ClinicalQueueConsumer outpatientMvp = consumer(true);
        String payload = """
                {"eventId":"%s","recordId":"%s","labId":"%s","conclusion":"Normal"}
                """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> outpatientMvp.consume(message(RabbitConfig.LAB_RESULT_CREATED, payload)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("lacks outpatient identity");
        verifyNoInteractions(labResults, inbox);
    }

    @Test
    void outpatientMvp_quarantinesFlatV0PrescriptionBeforeReceiptAndEffect() {
        UUID eventId = UUID.randomUUID();
        ClinicalQueueConsumer outpatientMvp = consumer(true);
        String payload = """
                {"eventId":"%s","recordId":"%s","prescriptionId":"%s"}
                """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> outpatientMvp.consume(message(RabbitConfig.PRESCRIPTION_FILLED, payload)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("lacks outpatient identity");
        verifyNoInteractions(prescriptions, inbox);
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
        ArgumentCaptor<ConsumerInboxClaim> claim = ArgumentCaptor.forClass(ConsumerInboxClaim.class);
        verify(inbox).execute(claim.capture(), any(Runnable.class));
        assertThat(claim.getValue().operationKey()).isEqualTo("clinical:prescription:v0:" + prescriptionId);
    }

    private static Message message(String routingKey, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }

    private ClinicalQueueConsumer consumer(boolean outpatientMvp) {
        return new ClinicalQueueConsumer(objectMapper, labResults, prescriptions, inbox, evidenceFactory,
                new OutpatientMvpMode(outpatientMvp));
    }

    private static String readFixture(String name) throws IOException {
        try (var input = ClinicalQueueConsumerTest.class.getResourceAsStream("/contracts/" + name)) {
            assertThat(input).as("canonical %s fixture", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
