package com.mediflow.lab.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.application.dto.command.FinancialClearanceCommand;
import com.mediflow.lab.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.lab.domain.model.ClearancePurpose;
import com.mediflow.lab.messaging.consumer.payload.FinancialClearanceEnvelope;
import com.mediflow.lab.messaging.consumer.payload.FinancialClearancePayload;

class FinancialClearanceConsumerTest {

    private final ReactToFinancialClearanceUseCase useCase = mock(ReactToFinancialClearanceUseCase.class);
    private final FinancialClearanceConsumer consumer = new FinancialClearanceConsumer(useCase);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void consume_canonicalLabTestFixture_mapsOnlyExplicitTestIds() throws Exception {
        try (InputStream fixture = getClass().getResourceAsStream(
                "/contracts/financial.clearance.granted.v1.json")) {
            FinancialClearanceEnvelope event = objectMapper.readValue(fixture, FinancialClearanceEnvelope.class);

            consumer.consume(event);
        }

        ArgumentCaptor<FinancialClearanceCommand> command = ArgumentCaptor.forClass(FinancialClearanceCommand.class);
        verify(useCase).onFinancialClearance(command.capture());
        assertThat(command.getValue().eventId()).isEqualTo(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        assertThat(command.getValue().purpose()).isEqualTo(ClearancePurpose.LAB_TEST);
        assertThat(command.getValue().labTestIds())
                .containsExactly(UUID.fromString("88888888-8888-4888-8888-888888888888"));
        assertThat(command.getValue().producer()).isEqualTo("billing-service");
    }

    @Test
    void consume_examClearance_rejectsWithoutForwarding() {
        FinancialClearanceEnvelope event = validEvent(ClearancePurpose.EXAM, List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("purpose");
        verify(useCase, org.mockito.Mockito.never()).onFinancialClearance(any());
    }

    @Test
    void consume_missingOrDuplicateTargetIds_rejectsInsteadOfInferring() {
        assertThatThrownBy(() -> consumer.consume(validEvent(ClearancePurpose.LAB_TEST, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("labTestIds");
        UUID testId = UUID.randomUUID();
        assertThatThrownBy(() -> consumer.consume(validEvent(ClearancePurpose.LAB_TEST,
                List.of(testId, testId))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("labTestIds");
        verify(useCase, org.mockito.Mockito.never()).onFinancialClearance(any());
    }

    @Test
    void consume_unknownVersion_rejectsToListenerDlqPath() {
        FinancialClearanceEnvelope base = validEvent(ClearancePurpose.LAB_TEST, List.of(UUID.randomUUID()));
        FinancialClearanceEnvelope event = new FinancialClearanceEnvelope(base.eventId(), base.eventType(), 2,
                base.occurredAt(), base.correlationId(), base.producer(), base.payload());

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
        verify(useCase, org.mockito.Mockito.never()).onFinancialClearance(any());
    }

    private static FinancialClearanceEnvelope validEvent(ClearancePurpose purpose, List<UUID> labTestIds) {
        return new FinancialClearanceEnvelope(UUID.randomUUID(), "financial.clearance.granted", 1,
                java.time.Instant.parse("2026-09-24T03:00:00Z"), "correlation-01", "billing-service",
                new FinancialClearancePayload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), com.mediflow.lab.domain.model.CareEpisodeType.OUTPATIENT_VISIT,
                        UUID.randomUUID(), purpose, null, UUID.randomUUID(), labTestIds, null, null, null,
                        new java.math.BigDecimal("250000.00"), "VND", "CASH", null, false));
    }
}
