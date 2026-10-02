package com.mediflow.report.application.port.in;

import java.util.UUID;
import com.mediflow.report.application.dto.response.OperationalReplayProgress;

/** Internal/offline only. No listener, API or active-generation pointer is attached. */
public interface ReplayOperationalProjectionUseCase {
    OperationalReplayProgress start();
    OperationalReplayProgress advance(UUID generationId, int batchSize);
}
