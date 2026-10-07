package com.mediflow.report.application.port.out;

import java.util.List;
import java.util.UUID;

import com.mediflow.report.application.dto.command.carefinance.CashReplayInput;
import com.mediflow.report.application.dto.response.CashReplayProgress;
import com.mediflow.report.domain.model.CashReceipt;

public interface CashReplayStorePort {
    CashReplayProgress freeze(UUID generationId);
    CashReplayProgress lock(UUID generationId);
    CashReplayProgress progress(UUID generationId);
    List<CashReplayInput> pending(UUID generationId, int limit);
    boolean insertReceipt(UUID generationId, CashReplayInput input);
    void incrementScope(UUID generationId, CashReceipt receipt, UUID departmentId);
    void markApplied(UUID generationId, UUID transactionId);
    CashReplayProgress reconcile(UUID generationId);
}
