package com.mediflow.lab.infrastructure.client;

import java.util.UUID;

/** Exact request body of Billing's account-locked target execution-start command. */
public record LabBillingExecutionStartRequest(
        UUID operationId,
        String sourceType,
        UUID sourceId,
        UUID patientId,
        String careEpisodeType,
        UUID careEpisodeId
) {
}
