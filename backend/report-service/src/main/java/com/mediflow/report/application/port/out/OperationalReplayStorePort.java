package com.mediflow.report.application.port.out;

import java.util.List;
import java.util.UUID;
import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.dto.response.OperationalReplayProgress;
import com.mediflow.report.domain.model.OperationalContribution;

public interface OperationalReplayStorePort {
    OperationalReplayProgress freeze(UUID generationId);
    OperationalReplayProgress lock(UUID generationId);
    List<ApplyOperationalContributionCommand> pending(UUID generationId, int limit);
    boolean insertContribution(UUID generationId, OperationalContribution fact);
    void incrementScope(UUID generationId, OperationalContribution fact, UUID departmentId);
    void markApplied(UUID generationId, UUID eventId);
    OperationalReplayProgress progress(UUID generationId);
    OperationalReplayProgress reconcile(UUID generationId);
}
