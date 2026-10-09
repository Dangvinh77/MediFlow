package com.mediflow.report.application.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.mediflow.report.application.dto.command.carefinance.RefundReplayInput;
import com.mediflow.report.application.dto.response.RefundReplayProgress;
import com.mediflow.report.domain.model.CashReceipt;

public interface RefundReplayStorePort {
    RefundReplayProgress freeze(UUID receiptGenerationId);
    RefundReplayProgress lock(UUID generationId);
    RefundReplayProgress progress(UUID generationId);
    List<RefundReplayInput> pending(UUID generationId, int batchSize);
    CashReceipt original(UUID generationId, UUID transactionId);
    BigDecimal acceptedRefundTotal(UUID generationId, UUID originalId);
    void restore(UUID generationId, RefundReplayInput input);
    RefundReplayProgress reconcile(UUID generationId);
}
