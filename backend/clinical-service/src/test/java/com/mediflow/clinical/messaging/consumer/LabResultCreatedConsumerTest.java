package com.mediflow.clinical.messaging.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

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
}
