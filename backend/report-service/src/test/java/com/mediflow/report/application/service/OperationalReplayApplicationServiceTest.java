package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.mediflow.report.application.dto.response.OperationalReplayProgress;
import com.mediflow.report.application.dto.response.OperationalReplayProgress.Status;
import com.mediflow.report.application.port.out.OperationalReplayStorePort;
import com.mediflow.report.domain.model.OperationalContribution;

class OperationalReplayApplicationServiceTest {
    private final OperationalReplayStorePort store = mock(OperationalReplayStorePort.class);
    private final OperationalReplayApplicationService service = new OperationalReplayApplicationService(store);
    private final UUID id = UUID.randomUUID();

    @Test
    void emptyFrozenManifest_isReconciledImmediately() {
        var empty = progress(Status.BUILDING, 0, 0);
        var verified = progress(Status.VERIFIED, 0, 0);
        when(store.freeze(any())).thenReturn(empty);
        when(store.reconcile(id)).thenReturn(verified);
        assertThat(service.start()).isEqualTo(verified);
        verify(store).reconcile(id);
    }

    @Test
    void nonemptyStart_onlyFreezesInputs() {
        var frozen = progress(Status.BUILDING, 2, 0);
        when(store.freeze(any())).thenReturn(frozen);
        assertThat(service.start()).isEqualTo(frozen);
        verify(store, never()).pending(any(), anyInt());
        verify(store, never()).reconcile(any());
    }

    @Test
    void invalidBatchDoesNotTouchStore() {
        for (int size : new int[]{0, -1, 501}) assertThatThrownBy(() -> service.advance(id, size)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.advance(null, 1)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }

    @Test
    void finishedGenerationIsStableNoOp() {
        for (Status status : List.of(Status.VERIFIED, Status.FAILED)) {
            when(store.lock(id)).thenReturn(progress(status, 1, 1));
            assertThat(service.advance(id, 1).status()).isEqualTo(status);
        }
        verify(store, never()).pending(any(), anyInt());
    }

    @Test
    void acceptedFact_updatesHospitalThenDepartmentThenMarksInput() {
        var command = command();
        var fact = command.contributions().get(0);
        when(store.lock(id)).thenReturn(progress(Status.BUILDING, 2, 0));
        when(store.pending(id, 1)).thenReturn(List.of(command));
        when(store.insertContribution(id, fact)).thenReturn(true);
        when(store.progress(id)).thenReturn(progress(Status.BUILDING, 2, 1));
        assertThat(service.advance(id, 1).appliedEvents()).isOne();
        var order = inOrder(store);
        order.verify(store).lock(id);
        order.verify(store).pending(id, 1);
        order.verify(store).insertContribution(id, fact);
        order.verify(store).incrementScope(id, fact, null);
        order.verify(store).incrementScope(id, fact, fact.departmentId());
        order.verify(store).markApplied(id, fact.eventId());
        verify(store, never()).reconcile(id);
    }

    @Test
    void duplicateFact_hasNoCounterDeltaButCompletesDeliveryAndReconciles() {
        var command = command();
        when(store.lock(id)).thenReturn(progress(Status.BUILDING, 1, 0));
        when(store.pending(id, 1)).thenReturn(List.of(command));
        when(store.progress(id)).thenReturn(progress(Status.BUILDING, 1, 1));
        when(store.reconcile(id)).thenReturn(progress(Status.VERIFIED, 1, 1));
        assertThat(service.advance(id, 1).status()).isEqualTo(Status.VERIFIED);
        verify(store, never()).incrementScope(any(), any(), any());
        verify(store).markApplied(id, command.event().metadata().eventId());
    }

    @Test
    void scopeFailure_isNotSwallowedOrMarkedApplied() {
        var command = command();
        var fact = command.contributions().get(0);
        when(store.lock(id)).thenReturn(progress(Status.BUILDING, 1, 0));
        when(store.pending(id, 1)).thenReturn(List.of(command));
        when(store.insertContribution(id, fact)).thenReturn(true);
        doThrow(new IllegalStateException("injected")).when(store).incrementScope(id, fact, fact.departmentId());
        assertThatThrownBy(() -> service.advance(id, 1)).hasMessage("injected");
        verify(store, never()).markApplied(any(), any());
        verify(store, never()).reconcile(any());
    }

    private OperationalReplayProgress progress(Status status, long sources, long applied) { return new OperationalReplayProgress(id, status, sources, applied); }

    private ApplyOperationalContributionCommand command() {
        UUID eventId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Instant time = Instant.parse("2026-10-01T08:00:00Z");
        return new ApplyOperationalContributionCommand(new DecodedCareFinanceEvent(new CareFinanceEventMetadata(eventId,
                "medicalrecord.completed", 1, time, "trace", "clinical-service", "recordId", sourceId), Map.of()),
                new OperationalContribution(eventId, "MEDICAL_RECORD", sourceId, 1, OperationalContribution.Metric.COMPLETED_VISITS,
                        UUID.randomUUID(), null, null, LocalDate.of(2026, 10, 1), BigDecimal.ONE, null, time));
    }
}
