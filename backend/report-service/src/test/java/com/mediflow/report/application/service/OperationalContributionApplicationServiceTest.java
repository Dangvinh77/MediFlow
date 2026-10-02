package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.report.application.dto.command.carefinance.*;
import com.mediflow.report.application.port.out.OperationalContributionStorePort;
import com.mediflow.report.domain.model.OperationalContribution;
import com.mediflow.report.domain.model.OperationalContribution.Metric;

class OperationalContributionApplicationServiceTest {
    private final OperationalContributionStorePort store = mock(OperationalContributionStorePort.class);
    private final OperationalContributionApplicationService service = new OperationalContributionApplicationService(store);
    private final UUID eventId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();
    private final Instant occurredAt = Instant.parse("2026-10-01T08:00:00Z");
    private final DecodedCareFinanceEvent event = new DecodedCareFinanceEvent(new CareFinanceEventMetadata(
            eventId, "prescription.filled", 1, occurredAt, "trace", "pharmacy-service", "dispenseId", sourceId), Map.of());

    @Test
    void twoMetrics_commitInStableScopeOrderAndJournalOnce() {
        var units = fact(Metric.DISPENSED_UNITS, BigDecimal.TEN, 1);
        var prescriptions = fact(Metric.DISPENSED_PRESCRIPTIONS, BigDecimal.ONE, 1);
        var command = new ApplyOperationalContributionCommand(event, List.of(units, prescriptions));
        when(store.claimDelivery(any())).thenReturn(true);
        when(store.insertContribution(any())).thenReturn(true);
        service.apply(command);
        var order = inOrder(store);
        order.verify(store).recordEvent(command);
        order.verify(store).claimDelivery(prescriptions);
        order.verify(store).insertContribution(prescriptions);
        order.verify(store).claimDelivery(units);
        order.verify(store).insertContribution(units);
        order.verify(store).incrementScope(prescriptions, null);
        order.verify(store).incrementScope(units, null);
        order.verify(store).incrementScope(prescriptions, departmentId);
        order.verify(store).incrementScope(units, departmentId);
        verifyNoMoreInteractions(store);
    }

    @Test
    void sameEventOrSameBusinessOperation_doesNotIncrementAgain() {
        var fact = fact(Metric.DISPENSED_PRESCRIPTIONS, BigDecimal.ONE, 1);
        var command = new ApplyOperationalContributionCommand(event, fact);
        service.apply(command);
        verify(store, never()).insertContribution(any());
        when(store.claimDelivery(fact)).thenReturn(true);
        service.apply(command);
        verify(store).insertContribution(fact);
        verify(store, never()).incrementScope(any(), any());
    }

    @Test
    void correctionVersionAndRepeatedMetric_areRejectedBeforeAnyStorageEffect() {
        assertThatThrownBy(() -> new ApplyOperationalContributionCommand(event,
                fact(Metric.DISPENSED_PRESCRIPTIONS, BigDecimal.ONE, 2))).hasMessageContaining("corrections");
        var fact = fact(Metric.DISPENSED_PRESCRIPTIONS, BigDecimal.ONE, 1);
        assertThatThrownBy(() -> new ApplyOperationalContributionCommand(event, List.of(fact, fact)))
                .hasMessageContaining("exactly once");
        verifyNoInteractions(store);
    }

    private OperationalContribution fact(Metric metric, BigDecimal value, int revision) {
        return new OperationalContribution(eventId, "DISPENSE", sourceId, revision, metric, departmentId,
                "ADMISSION", episodeId, LocalDate.of(2026, 10, 1), value, null, occurredAt);
    }
}
