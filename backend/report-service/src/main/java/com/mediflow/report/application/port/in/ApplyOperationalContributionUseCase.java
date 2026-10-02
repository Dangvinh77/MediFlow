package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;

/** Offline shadow-writer kernel; live source mappers/queues remain disabled until contract acceptance. */
public interface ApplyOperationalContributionUseCase {
    void apply(ApplyOperationalContributionCommand command);
}
