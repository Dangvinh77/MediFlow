package com.mediflow.billing.application.dto.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** The service identity and exact target are checked against current Billing ledger state. */
public record StartOutpatientExecutionCommand(@NotNull UUID operationId,
        @NotNull @Pattern(regexp = "EXAM|LAB_TEST") String sourceType, @NotNull UUID sourceId,
        @NotNull UUID patientId, @NotNull @Pattern(regexp = "OUTPATIENT_VISIT|ADMISSION") String careEpisodeType,
        @NotNull UUID careEpisodeId) { }
