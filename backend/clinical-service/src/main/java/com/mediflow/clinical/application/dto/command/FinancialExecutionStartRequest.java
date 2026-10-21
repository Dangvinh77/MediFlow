package com.mediflow.clinical.application.dto.command;

import java.util.UUID;

/** Exact outpatient source identity sent to Billing's execution-start admission endpoint. */
public record FinancialExecutionStartRequest(
        UUID operationId,
        String sourceType,
        UUID sourceId,
        UUID patientId,
        String careEpisodeType,
        UUID careEpisodeId
) {
}
