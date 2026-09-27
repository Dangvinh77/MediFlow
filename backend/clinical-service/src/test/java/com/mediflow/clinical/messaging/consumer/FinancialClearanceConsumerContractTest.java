package com.mediflow.clinical.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.application.dto.command.FinancialClearanceCommand;
import com.mediflow.clinical.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.clinical.messaging.consumer.payload.FinancialClearanceEvent;

class FinancialClearanceConsumerContractTest {
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
}
