package com.mediflow.clinical.application.dto.request;

import jakarta.validation.constraints.Min;

public record ClinicQueueCallRequest(@Min(1) int expectedRevision) {}
