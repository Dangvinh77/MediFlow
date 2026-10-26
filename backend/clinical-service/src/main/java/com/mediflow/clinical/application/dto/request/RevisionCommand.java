package com.mediflow.clinical.application.dto.request;

import jakarta.validation.constraints.Positive;

public record RevisionCommand(@Positive int expectedRevision) {}
