package com.mediflow.lab.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.application.dto.command.ConsumerInboxClaim;
import com.mediflow.lab.application.port.out.ConsumerInboxPort.InboxOutcome;
import com.mediflow.lab.application.port.out.LabOutpatientMvpMode;
import com.mediflow.lab.application.service.ConsumerInboxBoundary;
import com.mediflow.lab.infrastructure.config.RabbitConfig;
import com.mediflow.lab.messaging.consumer.inbox.InboxEvidenceFactory;
import com.mediflow.lab.messaging.consumer.payload.FinancialClearanceEnvelope;

class LabQueueConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MedicalRecordCreatedConsumer medicalRecords = mock(MedicalRecordCreatedConsumer.class);
    private final PaymentCompletedConsumer payments = mock(PaymentCompletedConsumer.class);
    private final FinancialClearanceConsumer clearances = mock(FinancialClearanceConsumer.class);
    private final ConsumerInboxBoundary inbox = mock(ConsumerInboxBoundary.class);
    private final InboxEvidenceFactory evidenceFactory = new InboxEvidenceFactory(objectMapper);
    private final LabQueueConsumer consumer = consumer(false);

    LabQueueConsumerTest() {
        when(inbox.execute(any(ConsumerInboxClaim.class), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return InboxOutcome.APPLIED;
        });
    }

    @Test
    void consume_medicalRecordV0_claimsNamespacedRecordAndDispatchesProjection() {
        UUID eventId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        consumer.consume(message(RabbitConfig.MEDICAL_RECORD_CREATED, """
                {"eventId":"%s","recordId":"%s","patientId":"%s","departmentId":"%s"}
                """.formatted(eventId, recordId, patientId, departmentId)));

        verify(medicalRecords).consume(argThat(payload -> eventId.equals(payload.eventId())
                && recordId.equals(payload.recordId()) && patientId.equals(payload.patientId())
                && departmentId.equals(payload.departmentId())));
        ArgumentCaptor<ConsumerInboxClaim> claim = ArgumentCaptor.forClass(ConsumerInboxClaim.class);
        verify(inbox).execute(claim.capture(), any(Runnable.class));
        org.assertj.core.api.Assertions.assertThat(claim.getValue().operationKey())
                .isEqualTo("lab:medical-record:v0:" + recordId);
    }

    @Test
    void consume_medicalRecordV1_claimsExactSourceFixtureOperation() throws Exception {
        String raw = readFixture("medicalrecord.created.v1.json");

        consumer.consume(message(RabbitConfig.MEDICAL_RECORD_CREATED, raw));

        verify(medicalRecords).consume(argThat(payload ->
                UUID.fromString("00000000-0000-4000-8000-000000000009").equals(payload.eventId())
                        && UUID.fromString("00000000-0000-4000-8000-000000000008").equals(payload.recordId())
                        && payload.sourceVersion() == 1));
        ArgumentCaptor<ConsumerInboxClaim> claim = ArgumentCaptor.forClass(ConsumerInboxClaim.class);
        verify(inbox).execute(claim.capture(), any(Runnable.class));
        org.assertj.core.api.Assertions.assertThat(claim.getValue().operationKey())
                .isEqualTo("lab:medical-record:v1:00000000-0000-4000-8000-000000000008");
        org.assertj.core.api.Assertions.assertThat(claim.getValue().rawBody())
                .containsExactly(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void consume_flatMedicalRecordCannotSpoofV1ProjectionFields() {
        UUID eventId = UUID.randomUUID();
        String spoof = """
                {"eventId":"%s","recordId":"%s","patientId":"%s","departmentId":"%s",
                 "sourceVersion":1,"eventType":"medicalrecord.created","version":1,
                 "producer":"clinical-service"}
                """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> consumer.consume(message(RabbitConfig.MEDICAL_RECORD_CREATED, spoof)))
                .isInstanceOf(org.springframework.amqp.support.converter.MessageConversionException.class);
        verifyNoInteractions(medicalRecords, inbox);
    }

    @Test
    void consume_incompleteMedicalRecordEnvelopeCannotFallBackToV0() {
        UUID eventId = UUID.randomUUID();
        String spoof = """
                {"eventId":"%s","eventType":"medicalrecord.created","version":1,
                 "producer":"clinical-service","recordId":"%s","patientId":"%s","departmentId":"%s"}
                """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> consumer.consume(message(RabbitConfig.MEDICAL_RECORD_CREATED, spoof)))
                .isInstanceOf(org.springframework.amqp.support.converter.MessageConversionException.class);
        verifyNoInteractions(medicalRecords, inbox);
    }

    @Test
    void consume_paymentRoute_claimsInvoiceAndExplicitLabTargets() {
        UUID eventId = UUID.randomUUID();
        UUID labTestId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        consumer.consume(message(RabbitConfig.PAYMENT_COMPLETED, """
                {"eventId":"%s","labTestIds":["%s"],"invoiceId":"%s"}
                """.formatted(eventId, labTestId, invoiceId)));

        verify(payments).consume(argThat(payload -> eventId.equals(payload.eventId())
                && payload.labTestIds().equals(java.util.List.of(labTestId))));
        ArgumentCaptor<ConsumerInboxClaim> claim = ArgumentCaptor.forClass(ConsumerInboxClaim.class);
        verify(inbox).execute(claim.capture(), any(Runnable.class));
        org.assertj.core.api.Assertions.assertThat(claim.getValue().operationKey())
                .isEqualTo("lab:payment:v0:" + invoiceId);
        org.assertj.core.api.Assertions.assertThat(claim.getValue().legacyEffectIds())
                .containsExactly(labTestId);
    }

    @Test
    void outpatientMvp_quarantinesV0PaymentBeforeReceiptAndEffect() {
        UUID eventId = UUID.randomUUID();
        LabQueueConsumer outpatientMvp = consumer(true);

        assertThatThrownBy(() -> outpatientMvp.consume(message(RabbitConfig.PAYMENT_COMPLETED, """
                {"eventId":"%s","invoiceId":"%s","labTestIds":["%s"]}
                """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID()))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("quarantined");

        verifyNoInteractions(payments, inbox);
    }

    @Test
    void outpatientMvp_quarantinesDeferredClearanceBeforeReceiptAndEffect() throws Exception {
        String deferred = readFixture("financial.clearance.granted.v1.json")
                .replace("\"purpose\": \"LAB_TEST\"", "\"purpose\": \"ADMISSION_DEPOSIT\"")
                .replace("\"careEpisodeType\": \"OUTPATIENT_VISIT\"", "\"careEpisodeType\": \"ADMISSION\"");
        LabQueueConsumer outpatientMvp = consumer(true);

        assertThatThrownBy(() -> outpatientMvp.consume(message(RabbitConfig.FINANCIAL_CLEARANCE_GRANTED, deferred)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("quarantined");

        verifyNoInteractions(clearances, inbox);
    }

    @Test
    void clearanceOperationUsesClearaceIdAndSetTargets() throws Exception {
        String raw = readFixture("financial.clearance.granted.v1.json");

        consumer.consume(message(RabbitConfig.FINANCIAL_CLEARANCE_GRANTED, raw));

        verify(clearances).consume(any(FinancialClearanceEnvelope.class));
        ArgumentCaptor<ConsumerInboxClaim> claim = ArgumentCaptor.forClass(ConsumerInboxClaim.class);
        verify(inbox).execute(claim.capture(), any(Runnable.class));
        org.assertj.core.api.Assertions.assertThat(claim.getValue().operationKey())
                .isEqualTo("lab:financial-clearance:v1:22222222-2222-4222-8222-222222222222");
        org.assertj.core.api.Assertions.assertThat(claim.getValue().legacyEffectIds())
                .containsExactly(UUID.fromString("88888888-8888-4888-8888-888888888888"));
    }

    private LabQueueConsumer consumer(boolean outpatientMvp) {
        return new LabQueueConsumer(objectMapper, medicalRecords, payments, clearances,
                new LabOutpatientMvpMode(outpatientMvp), inbox, evidenceFactory);
    }

    private static Message message(String routingKey, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }

    private static String readFixture(String name) throws Exception {
        try (var input = LabQueueConsumerTest.class.getResourceAsStream("/contracts/" + name)) {
            org.assertj.core.api.Assertions.assertThat(input).as("source fixture %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
