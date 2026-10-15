package com.mediflow.clinical.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
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
import com.mediflow.clinical.infrastructure.config.ClinicalClearanceRabbitConfig;
import com.mediflow.clinical.messaging.consumer.inbox.InboxEvidenceFactory;
import com.mediflow.clinical.messaging.consumer.payload.FinancialClearanceEvent;

class ClinicalFinancialClearanceQueueConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final FinancialClearanceConsumer businessConsumer = mock(FinancialClearanceConsumer.class);
    private final ConsumerInboxBoundary inbox = mock(ConsumerInboxBoundary.class);
    private final InboxEvidenceFactory evidenceFactory = new InboxEvidenceFactory(mapper);

    ClinicalFinancialClearanceQueueConsumerTest() {
        when(inbox.execute(any(ConsumerInboxClaim.class), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return InboxOutcome.APPLIED;
        });
    }

    @Test
    void outpatientMvp_rejectsDeferredPurposeBeforeReceiptOrEffect() throws Exception {
        String deferred = fixture().replace("\"purpose\": \"EXAM\"", "\"purpose\": \"ADMISSION_DEPOSIT\"");
        var consumer = consumer(true);

        assertThatThrownBy(() -> consumer.consume(message(deferred)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("quarantined");

        verifyNoInteractions(inbox, businessConsumer);
    }

    @Test
    void compatibilityModePreservesNonExamIgnoreWhileRecordingExactOperation() throws Exception {
        String raw = fixture().replace("\"purpose\": \"EXAM\"", "\"purpose\": \"LAB_TEST\"");
        var consumer = consumer(false);

        consumer.consume(message(raw));

        verify(businessConsumer).consume(any(FinancialClearanceEvent.class));
        ArgumentCaptor<ConsumerInboxClaim> claim = ArgumentCaptor.forClass(ConsumerInboxClaim.class);
        verify(inbox).execute(claim.capture(), any(Runnable.class));
        assertThat(claim.getValue().operationKey())
                .isEqualTo("clinical:clearance:v1:bb9cbdba-7b90-4c91-a4bd-fca4d8c0b08a");
    }

    @Test
    void purposeIsFingerprintDataButNeverPartOfClearanceOperationKey() throws Exception {
        var consumer = consumer(false);
        String exam = fixture();
        String lab = fixture().replace("\"purpose\": \"EXAM\"", "\"purpose\": \"LAB_TEST\"");

        consumer.consume(message(exam));
        consumer.consume(message(lab));

        ArgumentCaptor<ConsumerInboxClaim> claim = ArgumentCaptor.forClass(ConsumerInboxClaim.class);
        org.mockito.Mockito.verify(inbox, org.mockito.Mockito.times(2))
                .execute(claim.capture(), any(Runnable.class));
        assertThat(claim.getAllValues()).extracting(ConsumerInboxClaim::operationKey).containsOnly(
                "clinical:clearance:v1:bb9cbdba-7b90-4c91-a4bd-fca4d8c0b08a");
        assertThat(claim.getAllValues().get(0).canonicalBusinessBytes())
                .isNotEqualTo(claim.getAllValues().get(1).canonicalBusinessBytes());
    }

    @Test
    void malformedEnvelopeIsRejectedAsPoison() throws Exception {
        var consumer = consumer(false);
        String malformed = fixture().replace("\"producer\": \"billing-service\"", "\"producer\": \"unknown\"");

        assertThatThrownBy(() -> consumer.consume(message(malformed)))
                .isInstanceOf(MessageConversionException.class);
        verifyNoInteractions(inbox, businessConsumer);
    }

    private ClinicalFinancialClearanceQueueConsumer consumer(boolean outpatientMvp) {
        return new ClinicalFinancialClearanceQueueConsumer(mapper, businessConsumer, inbox, evidenceFactory,
                new OutpatientMvpMode(outpatientMvp));
    }

    private String fixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/contracts/financial.clearance.granted.v1.json")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Message message(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(ClinicalClearanceRabbitConfig.CLEARANCE_ROUTING_KEY);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }
}
