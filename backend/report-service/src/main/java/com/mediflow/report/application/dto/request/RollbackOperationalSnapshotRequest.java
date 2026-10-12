package com.mediflow.report.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record RollbackOperationalSnapshotRequest(@NotNull UUID commandId, @NotNull UUID generationId,
        @NotNull @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}") String acceptanceReference,
        @NotNull UUID expectedCurrentGenerationId) { }
