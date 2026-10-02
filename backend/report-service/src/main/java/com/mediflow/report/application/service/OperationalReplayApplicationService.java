package com.mediflow.report.application.service;

import java.util.ArrayList;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.dto.response.OperationalReplayProgress;
import com.mediflow.report.application.dto.response.OperationalReplayProgress.Status;
import com.mediflow.report.application.mapper.OperationalProjectionPlanner;
import com.mediflow.report.application.port.in.ReplayOperationalProjectionUseCase;
import com.mediflow.report.application.port.out.OperationalReplayStorePort;
import com.mediflow.report.domain.model.OperationalContribution;

/** Restartable finite rebuild. A DB generation lock serializes workers; each batch is one transaction. */
@Service
public class OperationalReplayApplicationService implements ReplayOperationalProjectionUseCase {
    private final OperationalReplayStorePort store;

    public OperationalReplayApplicationService(OperationalReplayStorePort store) { this.store = store; }

    @Override
    @Transactional
    public OperationalReplayProgress start() {
        var progress = store.freeze(UUID.randomUUID());
        return progress.sourceEvents() == 0 ? store.reconcile(progress.generationId()) : progress;
    }

    @Override
    @Transactional
    public OperationalReplayProgress advance(UUID generationId, int batchSize) {
        if (generationId == null || batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("Replay generation and batch size 1..500 are required");
        }
        var current = store.lock(generationId);
        if (current.status() != Status.BUILDING) return current;
        for (var command : store.pending(generationId, batchSize)) {
            var accepted = new ArrayList<OperationalContribution>();
            for (var fact : OperationalProjectionPlanner.ordered(command.contributions())) {
                if (store.insertContribution(generationId, fact)) accepted.add(fact);
            }
            for (var delta : OperationalProjectionPlanner.scopes(accepted)) {
                store.incrementScope(generationId, delta.fact(), delta.departmentId());
            }
            store.markApplied(generationId, command.event().metadata().eventId());
        }
        var progress = store.progress(generationId);
        return progress.appliedEvents() == progress.sourceEvents() ? store.reconcile(generationId) : progress;
    }
}
