package com.mediflow.clinical.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConversionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;
import com.mediflow.clinical.application.event.OutpatientEligibilityFact;
import com.mediflow.clinical.application.exception.ConsumerInboxConflictException;
import com.mediflow.clinical.application.service.ConsumerInboxBoundary;
import com.mediflow.clinical.application.service.OutpatientRegistrationApplicationService;
import com.mediflow.clinical.messaging.consumer.OutpatientEligibilityEventDecoder;
import com.mediflow.clinical.messaging.consumer.inbox.InboxEvidenceFactory;
import com.mediflow.clinical.messaging.consumer.payload.OutpatientFinancialEligibilityEvent;

class ClinicalOutpatientEligibilityConsumerTest {
    private static final UUID EVENT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID REQUEST_ID = UUID.fromString("a0000000-0000-0000-0000-000000000003");
    private static final UUID REGISTRATION_ID = UUID.fromString("a0000000-0000-0000-0000-000000000004");
    private static final UUID RESERVATION_ID = UUID.fromString("a0000000-0000-0000-0000-000000000005");
    private static final UUID PATIENT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000006");
    private static final UUID TRANSACTION_ID = UUID.fromString("a0000000-0000-0000-0000-000000000007");
    private static final UUID CLEARANCE_ID = UUID.fromString("a0000000-0000-0000-0000-000000000008");
    private static final UUID QUOTE_ID = UUID.fromString("a0000000-0000-0000-0000-000000000009");
    private static final Instant COMPLETED_AT = Instant.parse("2026-10-26T04:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final OutpatientRegistrationApplicationService registrations =
            mock(OutpatientRegistrationApplicationService.class);
    private final ConsumerInboxBoundary inbox = mock(ConsumerInboxBoundary.class);
    private final InboxEvidenceFactory evidenceFactory = new InboxEvidenceFactory(mapper);
    private ClinicalOutpatientEligibilityConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ClinicalOutpatientEligibilityConsumer(new OutpatientEligibilityEventDecoder(mapper),
                registrations, inbox, evidenceFactory);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return com.mediflow.clinical.application.port.out.ConsumerInboxPort.InboxOutcome.APPLIED;
        }).when(inbox).execute(any(ConsumerInboxClaim.class), any(Runnable.class));
    }

    @Test
    void patientPaymentFact_isInboxClaimedAndAppliedOnceWithOperationFenceKey() throws Exception {
        byte[] body = mapper.writeValueAsBytes(event(patientFact()));

        consumer.consume(new Message(body));

        var claim = org.mockito.ArgumentCaptor.forClass(ConsumerInboxClaim.class);
        verify(inbox).execute(claim.capture(), any(Runnable.class));
        assertThat(claim.getValue().consumerName()).isEqualTo("clinical-outpatient-eligibility");
        assertThat(claim.getValue().routingKey()).isEqualTo("outpatient.financial.eligibility.granted");
        assertThat(claim.getValue().operationKey()).isEqualTo(
                "clinical:outpatient-eligibility:v1:" + OPERATION_ID + ":19:COMPLETED");
        assertThat(claim.getValue().rawBody()).containsExactly(body);
        verify(registrations).consumeEligibility(patientFact());
    }

    @Test
    void zeroLiabilityFact_requiresStoredQuoteAndRealClearanceAndPreservesNullTransaction() throws Exception {
        OutpatientEligibilityFact fact = new OutpatientEligibilityFact(OPERATION_ID, REQUEST_ID, REGISTRATION_ID,
                RESERVATION_ID, 19, PATIENT_ID, "EXAM", REGISTRATION_ID, "OUTPATIENT_VISIT", REGISTRATION_ID,
                null, CLEARANCE_ID, "0.00", "VND", COMPLETED_AT, "ZERO_LIABILITY", QUOTE_ID);

        consumer.consume(new Message(mapper.writeValueAsBytes(event(fact))));

        verify(registrations).consumeEligibility(fact);
    }

    @Test
    void wrongProducerOrBinding_isRejectedBeforeInboxOrApplication() throws Exception {
        var invalid = new OutpatientFinancialEligibilityEvent(EVENT_ID,
                "outpatient.financial.eligibility.granted", 1, COMPLETED_AT, "corr-eligibility",
                "clinical-service", patientFact());

        assertThatThrownBy(() -> consumer.consume(new Message(mapper.writeValueAsBytes(invalid))))
                .isInstanceOf(MessageConversionException.class);

        verify(inbox, never()).execute(any(), any());
        verify(registrations, never()).consumeEligibility(any());
    }

    @Test
    void changedInboxEvidence_isQuarantined() throws Exception {
        doAnswer(invocation -> { throw new ConsumerInboxConflictException("event bytes changed"); })
                .when(inbox).execute(any(ConsumerInboxClaim.class), any(Runnable.class));

        assertThatThrownBy(() -> consumer.consume(new Message(mapper.writeValueAsBytes(event(patientFact())))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(registrations, never()).consumeEligibility(any());
    }

    private static OutpatientFinancialEligibilityEvent event(OutpatientEligibilityFact fact) {
        return new OutpatientFinancialEligibilityEvent(EVENT_ID, "outpatient.financial.eligibility.granted", 1,
                COMPLETED_AT, "corr-eligibility", "billing-service", fact);
    }

    private static OutpatientEligibilityFact patientFact() {
        return new OutpatientEligibilityFact(OPERATION_ID, REQUEST_ID, REGISTRATION_ID, RESERVATION_ID,
                19, PATIENT_ID, "EXAM", REGISTRATION_ID, "OUTPATIENT_VISIT", REGISTRATION_ID,
                TRANSACTION_ID, CLEARANCE_ID, "120.00", "VND", COMPLETED_AT, "PATIENT_PAYMENT", null);
    }
}
