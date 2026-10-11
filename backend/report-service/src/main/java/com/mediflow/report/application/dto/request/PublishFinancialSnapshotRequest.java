package com.mediflow.report.application.dto.request;

import com.mediflow.report.domain.model.FinancialPublicationPolicy.HistoryPolicy;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record PublishFinancialSnapshotRequest(@NotNull UUID commandId, @NotNull UUID generationId,
        UUID expectedCurrentGenerationId, @NotNull @Pattern(regexp="[a-f0-9]{64}") String manifestFingerprint,
        @NotNull @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}") String acceptanceReference,
        @NotNull HistoryPolicy historyPolicy) { }
