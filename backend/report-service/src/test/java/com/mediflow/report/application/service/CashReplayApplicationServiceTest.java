package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mediflow.report.application.dto.command.carefinance.CashReplayInput;
import com.mediflow.report.application.dto.response.CashReplayProgress;
import com.mediflow.report.application.dto.response.CashReplayProgress.Status;
import com.mediflow.report.application.port.out.CashReplayStorePort;
import com.mediflow.report.domain.model.CashReceipt;

class CashReplayApplicationServiceTest {
    private final CashReplayStorePort store = mock(CashReplayStorePort.class);
    private final CashReplayApplicationService service = new CashReplayApplicationService(store);
    private final UUID id = UUID.randomUUID();

    @Test
    void start_emptySource_verifiesOnlyTheFiniteEmptyManifest() {
        when(store.freeze(any())).thenReturn(progress(Status.BUILDING, 0, 0));
        when(store.reconcile(id)).thenReturn(progress(Status.VERIFIED, 0, 0));
        assertThat(service.start().status()).isEqualTo(Status.VERIFIED);
        verify(store).reconcile(id);
    }

    @Test
    void start_nonemptyManifest_doesNotApplyOrPublish() {
        when(store.freeze(any())).thenReturn(progress(Status.BUILDING, 2, 0));
        assertThat(service.start().sourceReceipts()).isEqualTo(2);
        verify(store).freeze(any()); verifyNoMoreInteractions(store);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 501})
    void advance_invalidBatch_rejectsBeforeLockOrEffects(int batch) {
        assertThatThrownBy(() -> service.advance(id, batch)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }

    @Test
    void advance_nullGeneration_rejectsBeforeLock() {
        assertThatThrownBy(() -> service.advance(null, 1)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }

    @ParameterizedTest @ValueSource(strings = {"VERIFIED", "FAILED"})
    void advance_terminalGeneration_performsNoWrite(String state) {
        when(store.lock(id)).thenReturn(progress(Status.valueOf(state), 2, 2));
        assertThat(service.advance(id, 1).status()).isEqualTo(Status.valueOf(state));
        verify(store).lock(id); verifyNoMoreInteractions(store);
    }

    @Test
    void advance_oneInput_factsThenBothScopesThenProgressAndReconciliation() {
        var input = input();
        when(store.lock(id)).thenReturn(progress(Status.BUILDING, 1, 0));
        when(store.pending(id, 1)).thenReturn(List.of(input));
        when(store.insertReceipt(id, input)).thenReturn(true);
        when(store.progress(id)).thenReturn(progress(Status.BUILDING, 1, 1));
        when(store.reconcile(id)).thenReturn(progress(Status.VERIFIED, 1, 1));
        assertThat(service.advance(id, 1).status()).isEqualTo(Status.VERIFIED);
        var order = inOrder(store);
        order.verify(store).lock(id); order.verify(store).pending(id, 1);
        order.verify(store).insertReceipt(id, input);
        order.verify(store).incrementScope(id, input.receipt(), null);
        order.verify(store).incrementScope(id, input.receipt(), input.receipt().departmentId());
        order.verify(store).markApplied(id, input.receipt().transactionId());
        order.verify(store).progress(id); order.verify(store).reconcile(id);
        verifyNoMoreInteractions(store);
    }

    @Test
    void advance_semanticDuplicate_marksInputButNeverAddsMoneyAgain() {
        var input = input();
        when(store.lock(id)).thenReturn(progress(Status.BUILDING, 2, 0));
        when(store.pending(id, 1)).thenReturn(List.of(input));
        when(store.progress(id)).thenReturn(progress(Status.BUILDING, 2, 1));
        assertThat(service.advance(id, 1).appliedReceipts()).isOne();
        verify(store, never()).incrementScope(any(), any(), any());
        verify(store).markApplied(id, input.receipt().transactionId());
        verify(store, never()).reconcile(id);
    }

    @Test
    void advance_scopeFailure_isPropagatedBeforeAppliedMarker() {
        var input = input();
        when(store.lock(id)).thenReturn(progress(Status.BUILDING, 1, 0));
        when(store.pending(id, 1)).thenReturn(List.of(input));
        when(store.insertReceipt(id, input)).thenReturn(true);
        doThrow(new IllegalStateException("scope failure")).when(store).incrementScope(id, input.receipt(), null);
        assertThatThrownBy(() -> service.advance(id, 1)).hasMessage("scope failure");
        verify(store, never()).markApplied(any(), any());
        verify(store, never()).reconcile(any());
    }

    @Test
    void advance_unsupportedGenerationVersion_failsWithoutDowngrade() {
        when(store.lock(id)).thenReturn(new CashReplayProgress(id, Status.BUILDING, 1, 0, 2, 1));
        assertThatThrownBy(() -> service.advance(id, 1)).hasMessageContaining("version");
        verify(store).lock(id); verifyNoMoreInteractions(store);
    }

    @Test
    void advance_exhaustedManifestWithProgressDrift_reconcilesInsteadOfLoopingForever() {
        when(store.lock(id)).thenReturn(progress(Status.BUILDING, 1, 0));
        when(store.pending(id, 1)).thenReturn(List.of());
        when(store.reconcile(id)).thenReturn(progress(Status.FAILED, 1, 0));
        assertThat(service.advance(id, 1).status()).isEqualTo(Status.FAILED);
        verify(store).reconcile(id);
        verify(store, never()).insertReceipt(any(), any());
    }

    @Test
    void progress_isReadOnlyAndRequiresExactGeneration() {
        when(store.progress(id)).thenReturn(progress(Status.BUILDING, 1, 0));
        assertThat(service.progress(id).generationId()).isEqualTo(id);
        verify(store).progress(id); verifyNoMoreInteractions(store);
        assertThatThrownBy(() -> service.progress(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private CashReplayProgress progress(Status status, long source, long applied) {
        return new CashReplayProgress(id, status, source, applied, 1, 1);
    }

    private static CashReplayInput input() {
        var receipt = new CashReceipt(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ADMISSION", UUID.randomUUID(), CashReceipt.Classification.ADMISSION_DEPOSIT,
                new BigDecimal("100.00"), "VND", "CASH", Instant.parse("2026-10-05T08:00:00Z"),
                LocalDate.of(2026, 10, 5), "Asia/Bangkok");
        return new CashReplayInput(receipt, UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "c".repeat(64));
    }
}
