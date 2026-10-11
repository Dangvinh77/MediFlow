package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.response.FinancialRebuildProgress;
import java.time.LocalDate;
import java.util.UUID;

/** Bounded local rebuild; an operator adapter may call it, but verification never publishes itself. */
public interface RebuildFinancialSourcesUseCase {
    FinancialRebuildProgress start(LocalDate from, LocalDate to, String reportZone);
    FinancialRebuildProgress advance(UUID generationId, int batchDays);
    FinancialRebuildProgress progress(UUID generationId);
}
