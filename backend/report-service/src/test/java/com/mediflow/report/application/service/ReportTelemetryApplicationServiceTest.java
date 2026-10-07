package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mediflow.report.application.dto.response.ReportTelemetrySnapshot;
import com.mediflow.report.application.dto.response.ReportTelemetrySnapshot.Replay;
import com.mediflow.report.application.port.out.ReportTelemetryReadPort;

class ReportTelemetryApplicationServiceTest {
    private final ReportTelemetryReadPort store = mock(ReportTelemetryReadPort.class);
    private final ReportTelemetryApplicationService service = new ReportTelemetryApplicationService(store);

    @Test
    void capture_returnsExactPortSnapshotWithoutInventingAcceptance() {
        var empty = new Replay(0, 0, 0, 0, 0, null);
        var snapshot = new ReportTelemetrySnapshot(Instant.EPOCH, 0, null, 0, empty, empty);
        when(store.capture()).thenReturn(snapshot);
        assertThat(service.capture()).isSameAs(snapshot);
    }

    @Test
    void capture_failurePropagatesSoCollectorCanMarkUnavailable() {
        when(store.capture()).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(service::capture).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"negative", "progress", "missingAge", "emptyAge", "emptySources"})
    void replay_invalidAggregateIsNotHealthy(String scenario) {
        assertThatThrownBy(() -> {
            switch (scenario) {
                case "negative" -> new Replay(-1, 0, 0, 0, 0, null);
                case "progress" -> new Replay(1, 0, 0, 1, 2, Instant.EPOCH);
                case "missingAge" -> new Replay(1, 0, 0, 1, 0, null);
                case "emptyAge" -> new Replay(0, 0, 0, 0, 0, Instant.EPOCH);
                default -> new Replay(0, 0, 0, 1, 0, null);
            }
        }).hasMessage("Invalid aggregate telemetry state");
    }

    @Test
    void pending_missingObservedTimeIsNotAcceptedAsZeroAge() {
        var empty = new Replay(0, 0, 0, 0, 0, null);
        assertThatThrownBy(() -> new ReportTelemetrySnapshot(Instant.EPOCH, 1, null, 0, empty, empty))
                .hasMessage("Invalid aggregate telemetry state");
    }
}
