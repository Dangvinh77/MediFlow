package com.mediflow.report.application.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.mediflow.report.application.dto.command.carefinance.RefundReplayInput.State;
import com.mediflow.report.application.dto.response.CashReplayProgress.Status;
import com.mediflow.report.application.dto.response.RefundReplayProgress;
import com.mediflow.report.application.port.in.ReplayCashReceiptsUseCase;
import com.mediflow.report.application.port.in.ReplayCashRefundsUseCase;
import com.mediflow.report.application.port.out.RefundReplayStorePort;

/** Paired frozen receipt/refund manifests, no live replay or publication. Old V13 runs stay unchanged. */
@Service
public class RefundReplayApplicationService implements ReplayCashRefundsUseCase {
    private final ReplayCashReceiptsUseCase receipts;
    private final RefundReplayStorePort store;
    public RefundReplayApplicationService(ReplayCashReceiptsUseCase receipts, RefundReplayStorePort store) {
        this.receipts = receipts; this.store = store;
    }
    @Override @Transactional(isolation = Isolation.REPEATABLE_READ)
    public RefundReplayProgress start() {
        // Both freeze statements must share one PostgreSQL MVCC snapshot, not separate timestamps.
        var receiptRun = receipts.start();
        var run = store.freeze(receiptRun.generationId()); supported(run);
        return receiptRun.status() == Status.VERIFIED && run.sourceRefunds() == 0
                ? store.reconcile(run.generationId()) : run;
    }
    @Override @Transactional
    public RefundReplayProgress advance(UUID id, int batchSize) {
        if (id == null || batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("Refund replay batch must be 1..500");
        var current = store.lock(id); supported(current);
        if (current.status() != Status.BUILDING) return current;
        var receiptRun = receipts.advance(id, batchSize);
        if (receiptRun.status() == Status.FAILED) return store.reconcile(id);
        if (receiptRun.status() != Status.VERIFIED) return current;
        var inputs = store.pending(id, batchSize);
        for (var input : inputs) {
            if (input.state() == State.APPLIED) {
                var refund = input.refund();
                var original = store.original(id, refund.originalTransactionId());
                refund.verifyOriginal(original, store.acceptedRefundTotal(id, refund.originalTransactionId()).subtract(refund.amount()));
                if (original.classification() != input.classification() || !original.businessDate().equals(input.originalBusinessDate())) {
                    throw new IllegalArgumentException("Frozen refund classification/date conflict");
                }
            }
            store.restore(id, input);
        }
        var progress = store.progress(id);
        return inputs.isEmpty() || progress.sourceRefunds() == progress.processedRefunds() ? store.reconcile(id) : progress;
    }
    @Override @Transactional(readOnly = true)
    public RefundReplayProgress progress(UUID id) {
        if (id == null) throw new IllegalArgumentException("Refund replay generation required");
        return store.progress(id);
    }
    private static void supported(RefundReplayProgress progress) {
        if (progress.formatVersion() != 1) throw new IllegalArgumentException("Unsupported refund replay format");
    }
}
