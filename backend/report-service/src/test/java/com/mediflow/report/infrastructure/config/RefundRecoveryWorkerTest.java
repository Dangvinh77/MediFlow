package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import com.mediflow.report.application.port.in.ApplyCashRefundUseCase;
import com.mediflow.report.application.port.out.CashRefundStorePort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundRecoveryWorkerTest {
    private final CashRefundStorePort store = mock(CashRefundStorePort.class);
    private final ApplyCashRefundUseCase refunds = mock(ApplyCashRefundUseCase.class);
    private final ReportCashRefundConsumerConfiguration.RefundRecoveryWorker worker =
            new ReportCashRefundConsumerConfiguration.RefundRecoveryWorker(store, refunds);

    @Test void recover_requestsBoundedCandidatesAndDoesNotInventMissingOriginals() {
        when(store.readyOriginals(20)).thenReturn(List.of());
        worker.recover();
        verify(store).readyOriginals(20);
        verifyNoMoreInteractions(store);
        verifyNoInteractions(refunds);
    }

    @Test void recover_passesExactOriginalIdentityWithoutDeferringSuccessfulWork() {
        UUID original = UUID.randomUUID();
        when(store.readyOriginals(20)).thenReturn(List.of(original));
        worker.recover();
        verify(refunds).recover(original);
        verify(store, never()).defer(any());
    }

    @Test void recover_defersFailedTransactionAndContinuesHealthyOriginal() {
        UUID failed = UUID.randomUUID(), healthy = UUID.randomUUID();
        when(store.readyOriginals(20)).thenReturn(List.of(failed, healthy));
        doThrow(new IllegalStateException("storage unavailable")).when(refunds).recover(failed);
        worker.recover();
        var order = inOrder(store, refunds);
        order.verify(refunds).recover(failed);
        order.verify(store).defer(failed);
        order.verify(refunds).recover(healthy);
        verify(store, never()).defer(healthy);
    }

    @Test void recover_failedDeferralDoesNotEraseEvidenceOrStopOtherOriginals() {
        UUID failed = UUID.randomUUID(), healthy = UUID.randomUUID();
        when(store.readyOriginals(20)).thenReturn(List.of(failed, healthy));
        doThrow(new IllegalStateException("storage unavailable")).when(refunds).recover(failed);
        doThrow(new IllegalStateException("deferral unavailable")).when(store).defer(failed);
        assertThatCode(worker::recover).doesNotThrowAnyException();
        verify(refunds).recover(healthy);
        verify(store, never()).reject(any(), any());
    }

    @Test void recover_candidateReadOutageHasNoEffectsAndNextPollCanProceed() {
        UUID original = UUID.randomUUID();
        when(store.readyOriginals(20)).thenThrow(new IllegalStateException("storage unavailable"))
                .thenReturn(List.of(original));
        assertThatCode(worker::recover).doesNotThrowAnyException();
        verifyNoInteractions(refunds);
        worker.recover();
        verify(refunds).recover(original);
        verify(store, never()).defer(any());
    }
}
