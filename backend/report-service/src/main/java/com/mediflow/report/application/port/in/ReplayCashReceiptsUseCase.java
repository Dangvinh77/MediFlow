package com.mediflow.report.application.port.in;

import java.util.UUID;

import com.mediflow.report.application.dto.response.CashReplayProgress;

/** Internal/offline only, no automatic writer/listener, financial API or publication. */
public interface ReplayCashReceiptsUseCase {
    CashReplayProgress start();
    CashReplayProgress advance(UUID generationId, int batchSize);
    CashReplayProgress progress(UUID generationId);
}
