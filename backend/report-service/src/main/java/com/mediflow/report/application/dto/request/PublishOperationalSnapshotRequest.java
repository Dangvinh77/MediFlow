package com.mediflow.report.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;

/** Explicit acceptance of retained local SURGERY inputs, not upstream completeness. */
public record PublishOperationalSnapshotRequest(@NotNull UUID commandId, @NotNull UUID generationId,
        @NotNull @Pattern(regexp="[a-f0-9]{64}") String manifestFingerprint,
        @NotNull LocalDate coveredFrom, @NotNull LocalDate coveredTo,
        @NotNull @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}") String acceptanceReference,
        UUID expectedCurrentGenerationId) { }
