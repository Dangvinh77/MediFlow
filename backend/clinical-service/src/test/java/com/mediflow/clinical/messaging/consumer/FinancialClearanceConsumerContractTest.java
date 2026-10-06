package com.mediflow.clinical.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.application.dto.command.FinancialClearanceCommand;
import com.mediflow.clinical.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.clinical.domain.model.ClearancePurpose;
import com.mediflow.clinical.messaging.consumer.payload.FinancialClearanceEvent;

class FinancialClearanceConsumerContractTest {
    @Test
    void consume_billingProducerBytes_preservesExactExamTargets() throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "../billing-service/src/test/resources/contracts/ledger-v1/clearance-exam.json"));
        consumer.consume(objectMapper.readValue(bytes, FinancialClearanceEvent.class));
        var command = org.mockito.ArgumentCaptor.forClass(FinancialClearanceCommand.class);
        verify(useCase).onFinancialClearance(command.capture());
        assertThat(command.getValue().appointmentId()).isEqualTo(command.getValue().careEpisodeId());
        assertThat(command.getValue().recordId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        assertThat(command.getValue().purpose()).isEqualTo(ClearancePurpose.EXAM);
    }
    private final ReactToFinancialClearanceUseCase useCase = mock(ReactToFinancialClearanceUseCase.class);
    private final FinancialClearanceConsumer consumer = new FinancialClearanceConsumer(useCase);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void consume_canonicalV1Fixture_preservesAppointmentEpisodeAndTarget() throws IOException {
        FinancialClearanceEvent event = objectMapper.readValue(
                getClass().getResourceAsStream("/contracts/financial.clearance.granted.v1.json"),
                FinancialClearanceEvent.class);

        consumer.consume(event);

        var captor = org.mockito.ArgumentCaptor.forClass(FinancialClearanceCommand.class);
        verify(useCase).onFinancialClearance(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(UUID.fromString("7cfe50b1-0a5d-48f4-b2fc-f0889e8c7f01"));
        assertThat(captor.getValue().purpose().name()).isEqualTo("EXAM");
        assertThat(captor.getValue().appointmentId()).isEqualTo(captor.getValue().careEpisodeId());
        assertThat(captor.getValue().recordId()).isNull();
        assertThat(captor.getValue().producer()).isEqualTo("billing-service");
    }

    @Test
    void consume_unknownEnvelopeVersion_rejectsForDeadLetter() {
        FinancialClearanceEvent event = new FinancialClearanceEvent(UUID.randomUUID(),
                "financial.clearance.granted", 2, java.time.Instant.now(), "correlation",
                "billing-service", null);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consume_clearancesForOtherPurposes_acknowledgesWithoutCallingExamUseCase() throws IOException {
        for (ClearancePurpose purpose : ClearancePurpose.values()) {
            if (purpose != ClearancePurpose.EXAM) {
                consumer.consume(eventForPurpose(purpose));
            }
        }

        verifyNoInteractions(useCase);
    }

    @Test
    void consume_unknownPurpose_rejectsInsteadOfAcknowledging() throws IOException {
        FinancialClearanceEvent event = eventForPurpose(ClearancePurpose.LAB_TEST);
        ObjectNode json = objectMapper.valueToTree(event);
        ((ObjectNode) json.get("payload")).put("purpose", "UNKNOWN");
        FinancialClearanceEvent malformed = objectMapper.treeToValue(json, FinancialClearanceEvent.class);

        assertThatThrownBy(() -> consumer.consume(malformed))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(useCase);
    }

    @Test
    void consume_unrelatedPurposeWithMalformedCommonPayload_rejects() throws IOException {
        FinancialClearanceEvent event = eventForPurpose(ClearancePurpose.LAB_TEST);
        ObjectNode json = objectMapper.valueToTree(event);
        ((ObjectNode) json.get("payload")).putNull("currency");
        FinancialClearanceEvent malformed = objectMapper.treeToValue(json, FinancialClearanceEvent.class);

        assertThatThrownBy(() -> consumer.consume(malformed))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(useCase);
    }

    private FinancialClearanceEvent eventForPurpose(ClearancePurpose purpose) throws IOException {
        ObjectNode json = (ObjectNode) objectMapper.readTree(
                getClass().getResourceAsStream("/contracts/financial.clearance.granted.v1.json"));
        ObjectNode payload = (ObjectNode) json.get("payload");
        payload.put("purpose", purpose.name());
        payload.putNull("appointmentId");
        payload.putNull("recordId");
        payload.putArray("labTestIds");
        payload.putNull("prescriptionId");
        payload.putNull("admissionId");
        payload.putNull("surgeryCaseId");

        if (purpose == ClearancePurpose.LAB_TEST) {
            payload.withArray("labTestIds").add(UUID.randomUUID().toString());
        } else if (purpose == ClearancePurpose.PRESCRIPTION) {
            payload.put("prescriptionId", UUID.randomUUID().toString());
        } else if (purpose == ClearancePurpose.ADMISSION_DEPOSIT || purpose == ClearancePurpose.SURGERY) {
            UUID admissionId = UUID.randomUUID();
            payload.put("careEpisodeType", "ADMISSION");
            payload.put("careEpisodeId", admissionId.toString());
            payload.put("admissionId", admissionId.toString());
            if (purpose == ClearancePurpose.SURGERY) {
                payload.put("surgeryCaseId", UUID.randomUUID().toString());
            }
        }

        return objectMapper.treeToValue(json, FinancialClearanceEvent.class);
    }
}
