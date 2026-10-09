package com.mediflow.report.application.port.in;

import java.util.UUID;
import com.mediflow.report.application.dto.response.RefundReplayProgress;

public interface ReplayCashRefundsUseCase {
    RefundReplayProgress start();
    RefundReplayProgress advance(UUID generationId, int batchSize);
    RefundReplayProgress progress(UUID generationId);
}
