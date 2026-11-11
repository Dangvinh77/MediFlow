package com.mediflow.clinical.application.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AddCourseVisitRequest(@NotNull UUID recordId) {}
