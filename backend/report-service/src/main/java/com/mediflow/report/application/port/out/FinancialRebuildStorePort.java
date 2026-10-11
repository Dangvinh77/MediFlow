package com.mediflow.report.application.port.out;

import com.mediflow.report.application.dto.response.FinancialRebuildProgress;
import com.mediflow.report.domain.model.FinancialSourceManifest;
import com.mediflow.report.domain.model.FinancialSourceProjection;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FinancialRebuildStorePort {
    FinancialRebuildProgress freeze(UUID generationId, LocalDate from, LocalDate to, String zone);
    FinancialRebuildProgress lock(UUID generationId);
    FinancialRebuildProgress progress(UUID generationId);
    FinancialSourceManifest manifest(FinancialRebuildProgress progress);
    void writeDays(FinancialRebuildProgress progress, LocalDate through, List<FinancialSourceProjection.Day> rows);
    FinancialRebuildProgress reconcile(FinancialRebuildProgress progress, List<FinancialSourceProjection.Day> expected);
    FinancialRebuildProgress fail(UUID generationId, String reasonCode);
}
