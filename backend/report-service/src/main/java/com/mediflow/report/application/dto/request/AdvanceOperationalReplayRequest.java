package com.mediflow.report.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdvanceOperationalReplayRequest(@NotNull @Min(1) @Max(500) Integer batchSize) { }
