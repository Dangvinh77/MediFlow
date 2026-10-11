package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.response.FinancialRebuildProgress;
import com.mediflow.report.application.dto.response.FinancialRebuildProgress.Status;
import com.mediflow.report.application.port.in.RebuildFinancialSourcesUseCase;
import com.mediflow.report.application.port.out.FinancialRebuildStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.FinancialSourceProjection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialRebuildApplicationService implements RebuildFinancialSourcesUseCase {
    public static final int MAX_BATCH_DAYS = 31;
    private final FinancialRebuildStorePort store;

    public FinancialRebuildApplicationService(FinancialRebuildStorePort store) { this.store = store; }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 15)
    public FinancialRebuildProgress start(LocalDate from, LocalDate to, String reportZone) {
        if (from == null || to == null || from.getYear() < 2000 || to.getYear() > 2100
                || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 366 || reportZone == null) {
            throw invalid();
        }
        try { ZoneId.of(reportZone); } catch (java.time.DateTimeException error) { throw invalid(); }
        return store.freeze(UUID.randomUUID(), from, to, reportZone);
    }

    @Override
    @Transactional(timeout = 15)
    public FinancialRebuildProgress advance(UUID generationId, int batchDays) {
        if (generationId == null || batchDays < 1 || batchDays > MAX_BATCH_DAYS) throw invalid();
        var current = store.lock(generationId);
        if (current.status() != Status.BUILDING) return current;
        if (current.unresolvedInputs() != 0) return store.fail(generationId, "REPORT_FINANCIAL_SOURCE_UNRESOLVED");
        List<FinancialSourceProjection.Day> expected;
        try {
            var manifest = store.manifest(current);
            if (manifest.size() != current.sourceInputs()) throw invalid();
            expected = manifest.project(current.coveredFrom(), current.coveredTo(), current.reportZone(), current.sourceObservedAt());
        } catch (ReportRuleException error) {
            return store.fail(generationId, error.getCode());
        } catch (IllegalArgumentException error) {
            return store.fail(generationId, "REPORT_FINANCIAL_MANIFEST_INVALID");
        }
        LocalDate next = current.coveredFrom().plusDays(current.processedDays());
        if (next.isAfter(current.coveredTo())) return store.reconcile(current, expected);
        LocalDate through = next.plusDays(batchDays - 1);
        if (through.isAfter(current.coveredTo())) through = current.coveredTo();
        final LocalDate end = through;
        var batch = expected.stream().filter(row -> !row.businessDate().isBefore(next) && !row.businessDate().isAfter(end)).toList();
        // Storage failures are deliberately outside the business-validation catch: rollback every row/checkpoint.
        store.writeDays(current, through, batch);
        var progress = store.progress(generationId);
        return through.equals(current.coveredTo()) ? store.reconcile(progress, expected) : progress;
    }

    @Override
    @Transactional(readOnly = true, timeout = 5)
    public FinancialRebuildProgress progress(UUID generationId) {
        if (generationId == null) throw invalid();
        return store.progress(generationId);
    }

    private static ReportRuleException invalid() {
        return new ReportRuleException("REPORT_FINANCIAL_REBUILD_INVALID", "Bounded financial rebuild input required");
    }
}
