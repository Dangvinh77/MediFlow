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
import java.io.IOException;
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
    void missingProducerIsRejectedBeforeApplyingEvent() {
        String event = admissionRequestedEnvelope("true")
                .replace("\"producer\": \"clinical-service\",\n", "");

        assertThatThrownBy(() -> consumer.receive(message(event)))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("producer");

        verifyNoInteractions(referrals, clearances, settlements, topups, externalOrders);
    }

    @Test
    void wrongProducerIsRejectedBeforeApplyingEvent() {
        String event = admissionRequestedEnvelope("true")
                .replace("\"producer\": \"clinical-service\"", "\"producer\": \"billing-service\"");

        assertThatThrownBy(() -> consumer.receive(message(event)))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("producer");

        verifyNoInteractions(referrals, clearances, settlements, topups, externalOrders);
    }

    @Test
    void inpatientLabResultUsesCanonicalLabFixtureAndCareEpisodeIdentifiers() throws IOException {
        consumer.receive(message(readFixture("lab.result.created.admission.v1.json")));

        var command = org.mockito.ArgumentCaptor.forClass(ExternalOrderFactCommand.class);
        verify(externalOrders).onExternalOrderFact(command.capture());
        assertThat(command.getValue()).isInstanceOf(LabResultFactCommand.class);
        LabResultFactCommand labResult = (LabResultFactCommand) command.getValue();
        assertThat(labResult.phienBan()).isEqualTo(1);
        assertThat(labResult.maTuongQuan()).isEqualTo("correlation-123");
        assertThat(labResult.maYLenhBenNgoai()).isEqualTo(UUID.fromString(LAB_ID));
        assertThat(labResult.maDotNoiTru()).isEqualTo(UUID.fromString(ADMISSION_ID));
        assertThat(labResult.maBenhNhan()).isEqualTo(UUID.fromString(PATIENT_ID));
        assertThat(labResult.phienBanKetQua()).isEqualTo(1);
        assertThat(labResult.ketLuan()).isEqualTo("No acute finding");
    }

    @Test
    void outpatientLabResultIsIgnoredWithoutApplyingAdmissionUseCase() {
        assertThatCode(() -> consumer.receive(message(labResultEnvelope("OUTPATIENT_VISIT", 1))))
                .doesNotThrowAnyException();

        verifyNoInteractions(externalOrders);
    }

    @Test
    void versionedLabResultWithoutCareEpisodeTypeIsRejected() {
        assertThatThrownBy(() -> consumer.receive(message(legacyLabResultEnvelope())))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("careEpisodeType");

        verifyNoInteractions(externalOrders);
    }

    @Test
    void flatLegacyLabResultEventIsAcknowledgedWithoutV2EnvelopeFields() {
        assertThatCode(() -> consumer.receive(message(flatLegacyLabResultEvent())))
                .doesNotThrowAnyException();

        verifyNoInteractions(externalOrders);
    }

    @Test
    void flatLegacyPrescriptionFilledEventIsAcknowledgedWithoutAdmissionInference() {
        assertThatCode(() -> consumer.receive(message(flatLegacyPrescriptionFilledEvent())))
                .doesNotThrowAnyException();

        verifyNoInteractions(externalOrders);
    }

    @Test
    void versionedPrescriptionFilledWithoutAdmissionIdIsRejected() {
        assertThatThrownBy(() -> consumer.receive(message(prescriptionFilledEnvelopeWithoutAdmissionId())))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("admissionId");

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

    @Test
    void examClearanceWithoutAdmissionIdIsIgnoredBeforeAdmissionTargetParsing() {
        assertThatCode(() -> consumer.receive(message(financialClearanceEnvelope("EXAM"))))
                .doesNotThrowAnyException();

        verifyNoInteractions(clearances);
    }

    @Test
    void labTestClearanceWithoutAdmissionIdIsIgnoredBeforeAdmissionTargetParsing() {
        assertThatCode(() -> consumer.receive(message(financialClearanceEnvelope("LAB_TEST"))))
                .doesNotThrowAnyException();

        verifyNoInteractions(clearances);
    }

    @Test
    void malformedAdmissionDepositClearanceStillRequiresAdmissionId() {
        assertThatThrownBy(() -> consumer.receive(message(financialClearanceEnvelope("ADMISSION_DEPOSIT"))))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("admissionId");

        verifyNoInteractions(clearances);
    }

    private static Message message(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private String readFixture(String fixtureName) throws IOException {
        try (var input = getClass().getResourceAsStream("/contracts/" + fixtureName)) {
            assertThat(input).as("canonical %s fixture", fixtureName).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
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

    private static String flatLegacyLabResultEvent() {
        return """
                {
                  "eventId": "%s",
                  "occurredAt": "%s",
                  "correlationId": "correlation-123",
                  "labId": "%s",
                  "patientId": "%s",
                  "recordId": "%s",
                  "departmentId": "%s",
                  "labType": "CBC",
                  "performedDate": "2026-09-27",
                  "results": [{
                    "resultId": "00000000-0000-4000-8000-000000000009",
                    "indicator": "Hemoglobin",
                    "value": "13.5",
                    "unit": "g/dL",
                    "referenceRange": "12.0-16.0"
                  }],
                  "conclusion": "Within expected range"
                }
                """.formatted(EVENT_ID, OCCURRED_AT, LAB_ID, PATIENT_ID, RECORD_ID, DEPARTMENT_ID);
    }

    private static String flatLegacyPrescriptionFilledEvent() {
        return """
                {
                  "eventId": "%s",
                  "occurredAt": "%s",
                  "correlationId": "correlation-123",
                  "prescriptionId": "%s",
                  "recordId": "%s",
                  "patientId": "%s",
                  "departmentId": "%s",
                  "totalAmount": 125000.00,
                  "dispensedItems": [{
                    "drugId": "%s",
                    "drugName": "Amoxicillin",
                    "quantity": 10
                  }]
                }
                """.formatted(EVENT_ID, OCCURRED_AT, REQUEST_ID, RECORD_ID, PATIENT_ID,
                DEPARTMENT_ID, LAB_ID);
    }

    private static String prescriptionFilledEnvelopeWithoutAdmissionId() {
        return """
                {
                  "eventId": "%s",
                  "eventType": "prescription.filled",
                  "version": 1,
                  "occurredAt": "%s",
                  "correlationId": "correlation-123",
                  "producer": "pharmacy-service",
                  "payload": {
                    "prescriptionId": "%s",
                    "patientId": "%s",
                    "filledAt": "%s"
                  }
                }
                """.formatted(EVENT_ID, OCCURRED_AT, REQUEST_ID, PATIENT_ID, OCCURRED_AT);
    }

    private static String financialClearanceEnvelope(String purpose) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "financial.clearance.granted",
                  "version": 1,
                  "occurredAt": "%s",
                  "correlationId": "correlation-123",
                  "producer": "billing-service",
                  "payload": {
                    "clearanceId": "%s",
                    "invoiceId": "%s",
                    "accountId": "%s",
                    "patientId": "%s",
                    "careEpisodeType": "OUTPATIENT_VISIT",
                    "careEpisodeId": "%s",
                    "purpose": "%s",
                    "appointmentId": "%s",
                    "recordId": "%s",
                    "labTestIds": ["%s"],
                    "amount": 100000,
                    "currency": "VND",
                    "paymentMethod": "CASH",
                    "expiresAt": null,
                    "emergencyOverride": false
                  }
                }
                """.formatted(EVENT_ID, OCCURRED_AT, EVENT_ID, EVENT_ID, EVENT_ID, PATIENT_ID,
                REQUEST_ID, purpose, REQUEST_ID, RECORD_ID, LAB_ID);
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
                    "resultVersion": 1,
                    "conclusion": "No acute finding"
                  }
                }
                """.formatted(EVENT_ID, version, OCCURRED_AT, LAB_ID,
                        careEpisodeType, ADMISSION_ID, PATIENT_ID);
    }
}
