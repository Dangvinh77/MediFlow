package com.mediflow.inpatient.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.inpatient.application.dto.command.AdmissionRequestedCommand;
import com.mediflow.inpatient.application.dto.command.ExternalOrderFactCommand;
import com.mediflow.inpatient.application.dto.command.LabResultFactCommand;
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
    private static final String LAB_ID = "00000000-0000-4000-8000-000000000007";
    private static final String ADMISSION_ID = "00000000-0000-4000-8000-000000000008";
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

    @Test
    void inpatientLabResultUsesCanonicalLabAndCareEpisodeIdentifiers() {
        consumer.receive(message(inpatientLabResultEnvelope()));

        var command = org.mockito.ArgumentCaptor.forClass(ExternalOrderFactCommand.class);
        verify(externalOrders).onExternalOrderFact(command.capture());
        assertThat(command.getValue()).isInstanceOf(LabResultFactCommand.class);
        LabResultFactCommand labResult = (LabResultFactCommand) command.getValue();
        assertThat(labResult.phienBan()).isEqualTo(1);
        assertThat(labResult.maTuongQuan()).isEqualTo("correlation-123");
        assertThat(labResult.maYLenhBenNgoai()).isEqualTo(UUID.fromString(LAB_ID));
        assertThat(labResult.maDotNoiTru()).isEqualTo(UUID.fromString(ADMISSION_ID));
        assertThat(labResult.maBenhNhan()).isEqualTo(UUID.fromString(PATIENT_ID));
        assertThat(labResult.phienBanKetQua()).isEqualTo(3);
        assertThat(labResult.ketLuan()).isEqualTo("No acute finding");
    }

    @Test
    void outpatientLabResultIsIgnoredWithoutApplyingAdmissionUseCase() {
        assertThatCode(() -> consumer.receive(message(labResultEnvelope("OUTPATIENT_VISIT", 1))))
                .doesNotThrowAnyException();

        verifyNoInteractions(externalOrders);
    }

    @Test
    void legacyLabResultWithoutCareEpisodeTypeIsIgnored() {
        assertThatCode(() -> consumer.receive(message(legacyLabResultEnvelope())))
                .doesNotThrowAnyException();

        verifyNoInteractions(externalOrders);
    }

    @Test
    void admissionLabResultMissingRequiredLabIdIsRejected() {
        String malformed = inpatientLabResultEnvelope()
                .replace("\"labId\": \"" + LAB_ID + "\",", "");

        assertThatThrownBy(() -> consumer.receive(message(malformed)))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("labId");

        verifyNoInteractions(externalOrders);
    }

    @Test
    void unsupportedLabCareEpisodeTypeIsNotAppliedToAnAdmission() {
        assertThatThrownBy(() -> consumer.receive(message(labResultEnvelope("INPATIENT", 1))))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("unsupported careEpisodeType");

        verifyNoInteractions(externalOrders);
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

    private static String inpatientLabResultEnvelope() {
        return labResultEnvelope("ADMISSION", 1);
    }

    private static String legacyLabResultEnvelope() {
        return inpatientLabResultEnvelope()
                .replace("\"careEpisodeType\": \"ADMISSION\",", "");
    }

    private static String labResultEnvelope(String careEpisodeType, int version) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "lab.result.created",
                  "version": %d,
                  "occurredAt": "%s",
                  "correlationId": "correlation-123",
                  "producer": "lab-service",
                  "payload": {
                    "labId": "%s",
                    "careEpisodeType": "%s",
                    "careEpisodeId": "%s",
                    "patientId": "%s",
                    "resultVersion": 3,
                    "conclusion": "No acute finding"
                  }
                }
                """.formatted(EVENT_ID, version, OCCURRED_AT, LAB_ID,
                        careEpisodeType, ADMISSION_ID, PATIENT_ID);
    }
}
