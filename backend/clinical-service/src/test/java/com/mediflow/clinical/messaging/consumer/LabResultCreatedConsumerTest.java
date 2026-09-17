package com.mediflow.clinical.messaging.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mediflow.clinical.application.dto.command.LabResultCreatedCommand;
import com.mediflow.clinical.application.port.in.ReactToLabResultUseCase;
import com.mediflow.clinical.messaging.consumer.payload.LabResultCreatedPayload;

class LabResultCreatedConsumerTest {

    private final ReactToLabResultUseCase useCase = mock(ReactToLabResultUseCase.class);
    private final LabResultCreatedConsumer consumer = new LabResultCreatedConsumer(useCase);

    @Test
    void consume_mapsCanonicalIdentifiersAndConclusion() {
        UUID eventId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        LabResultCreatedPayload payload = new LabResultCreatedPayload(
                eventId, Instant.now(), "correlation-test", labId, UUID.randomUUID(), recordId,
                UUID.randomUUID(), "HEMATOLOGY", LocalDate.now(),
                List.of(new LabResultCreatedPayload.Result(
                        UUID.randomUUID(), "WBC", "7.2", "10^9/L", "4.0-10.0")),
                "Normal");

        consumer.consume(payload);

        verify(useCase).onLabResultCreated(
                new LabResultCreatedCommand(eventId, recordId, labId, "Normal"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventId", "recordId", "labId"})
    void consume_missingRequiredIdentifier_rejectsBeforeUseCase(String missingField) {
        UUID eventId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();
        LabResultCreatedPayload payload = new LabResultCreatedPayload(
                "eventId".equals(missingField) ? null : eventId,
                Instant.now(), "correlation-test",
                "labId".equals(missingField) ? null : labId,
                UUID.randomUUID(),
                "recordId".equals(missingField) ? null : recordId,
                UUID.randomUUID(), "HEMATOLOGY", LocalDate.now(), List.of(), "Normal");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(missingField);

        verifyNoInteractions(useCase);
    }
}
