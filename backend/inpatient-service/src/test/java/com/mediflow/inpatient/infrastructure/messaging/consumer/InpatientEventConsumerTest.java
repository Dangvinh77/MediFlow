package com.mediflow.inpatient.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.inpatient.application.dto.command.AdmissionRequestedCommand;
import com.mediflow.inpatient.application.port.in.ReactToAdmissionReferralUseCase;
import com.mediflow.inpatient.application.port.in.ReactToDepositTopupUseCase;
import com.mediflow.inpatient.application.port.in.ReactToExternalOrderUseCase;
import com.mediflow.inpatient.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.inpatient.application.port.in.ReactToSettlementUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class InpatientEventConsumerTest {

    private static final String EVENT_ID = "00000000-0000-4000-8000-000000000001";
    private static final String REQUEST_ID = "00000000-0000-4000-8000-000000000002";
    private static final String RECORD_ID = "00000000-0000-4000-8000-000000000003";
    private static final String PATIENT_ID = "00000000-0000-4000-8000-000000000004";
    private static final String DEPARTMENT_ID = "00000000-0000-4000-8000-000000000005";
    private static final String STAFF_ID = "00000000-0000-4000-8000-000000000006";
    private static final String OCCURRED_AT = "2026-09-27T04:00:00Z";

    private final ReactToAdmissionReferralUseCase referrals = mock(ReactToAdmissionReferralUseCase.class);
    private final ReactToFinancialClearanceUseCase clearances = mock(ReactToFinancialClearanceUseCase.class);
    private final ReactToSettlementUseCase settlements = mock(ReactToSettlementUseCase.class);
    private final ReactToDepositTopupUseCase topups = mock(ReactToDepositTopupUseCase.class);
    private final ReactToExternalOrderUseCase externalOrders = mock(ReactToExternalOrderUseCase.class);
    private InpatientEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InpatientEventConsumer(new ObjectMapper(), referrals, clearances,
                settlements, topups, externalOrders);
    }

    @Test
    void admissionReferralMapsCanonicalEnvelopeToApplicationCommand() {
        consumer.receive(message(admissionRequestedEnvelope("true")));

        var command = org.mockito.ArgumentCaptor.forClass(AdmissionRequestedCommand.class);
        verify(referrals).onAdmissionRequested(command.capture());
        assertThat(command.getValue().maSuKien()).isEqualTo(UUID.fromString(EVENT_ID));
        assertThat(command.getValue().maYeuCauNoiTru()).isEqualTo(UUID.fromString(REQUEST_ID));
        assertThat(command.getValue().maBenhNhan()).isEqualTo(UUID.fromString(PATIENT_ID));
        assertThat(command.getValue().capCuu()).isTrue();
        assertThat(command.getValue().xayRaLuc()).isEqualTo(Instant.parse(OCCURRED_AT));
    }

    @Test
    void malformedBooleanIsRejectedInsteadOfCoerced() {
        assertThatThrownBy(() -> consumer.receive(message(admissionRequestedEnvelope("1"))))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("emergency");

        verifyNoInteractions(referrals, clearances, settlements, topups, externalOrders);
    }

    private static Message message(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private static String admissionRequestedEnvelope(String emergency) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "admission.requested",
                  "version": 1,
                  "occurredAt": "%s",
                  "correlationId": "correlation-123",
                  "producer": "clinical-service",
                  "payload": {
                    "admissionRequestId": "%s",
                    "recordId": "%s",
                    "patientId": "%s",
                    "departmentId": "%s",
                    "requestedBy": "%s",
                    "diagnosisSummary": "Pneumonia",
                    "priority": "ROUTINE",
                    "emergency": %s,
                    "requestedAt": "%s"
                  }
                }
                """.formatted(EVENT_ID, OCCURRED_AT, REQUEST_ID, RECORD_ID, PATIENT_ID,
                DEPARTMENT_ID, STAFF_ID, emergency, OCCURRED_AT);
    }
}
