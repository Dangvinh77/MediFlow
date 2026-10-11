package com.mediflow.report.application.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record StartFinancialRebuildRequest(@NotNull LocalDate from, @NotNull LocalDate to) { }
