package com.mediflow.report.application.service;

import java.util.ArrayList;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.dto.response.CashReplayProgress;
import com.mediflow.report.application.dto.response.CashReplayProgress.Status;
import com.mediflow.report.application.mapper.CashProjectionPlanner;
import com.mediflow.report.application.port.in.ReplayCashReceiptsUseCase;
import com.mediflow.report.application.port.out.CashReplayStorePort;
import com.mediflow.report.domain.model.CashReceipt;

/** A DB generation lock fences workers; each batch joins facts, both scopes and progress atomically. */
@Service
public class CashReplayApplicationService implements ReplayCashReceiptsUseCase {
    public static final int MAX_BATCH_SIZE = 500;
    private final CashReplayStorePort store;

    public CashReplayApplicationService(CashReplayStorePort store) { this.store = store; }

    @Override
    @Transactional
    public CashReplayProgress start() {
        var current = store.freeze(UUID.randomUUID());
        supported(current);
        return current.sourceReceipts() == 0 ? store.reconcile(current.generationId()) : current;
    }

    @Override
    @Transactional
    public CashReplayProgress advance(UUID generationId, int batchSize) {
        if (generationId == null || batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Cash replay generation and batch size 1..500 are required");
        }
        var current = store.lock(generationId);
        supported(current);
        if (current.status() != Status.BUILDING) return current;
        var inputs = store.pending(generationId, batchSize);
        // An exhausted manifest with mismatched progress must fail reconciliation, not loop forever.
        if (inputs.isEmpty()) return store.reconcile(generationId);
        var accepted = new ArrayList<CashReceipt>();
        for (var input : inputs) {
            if (store.insertReceipt(generationId, input)) accepted.add(input.receipt());
        }
        for (var scope : CashProjectionPlanner.scopes(accepted)) {
            store.incrementScope(generationId, scope.receipt(), scope.departmentId());
        }
        for (var input : inputs) store.markApplied(generationId, input.receipt().transactionId());
        var progress = store.progress(generationId);
        return progress.sourceReceipts() == progress.appliedReceipts() ? store.reconcile(generationId) : progress;
    }

    @Override
    @Transactional(readOnly = true)
    public CashReplayProgress progress(UUID generationId) {
        if (generationId == null) throw new IllegalArgumentException("Cash replay generation is required");
        return store.progress(generationId);
    }

    private static void supported(CashReplayProgress progress) {
        // Local snapshot/projector versions are independent of the Billing wire schema version.
        if (progress.snapshotVersion() != 1 || progress.projectorVersion() != 1) {
            throw new IllegalArgumentException("Unsupported cash replay generation version");
        }
    }
}
