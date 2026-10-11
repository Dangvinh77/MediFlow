package com.mediflow.report.domain.model;

import com.mediflow.report.domain.exception.ReportRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Accepted local originals, not proof that an upstream historical export is complete. */
public record FinancialSourceManifest(List<CashReceipt> receipts, List<CashRefund> refunds,
        List<FinalSettlementRecognition> finals, List<SettlementReceivableSnapshot> balances) {
    public FinancialSourceManifest {
        receipts = List.copyOf(receipts);
        refunds = List.copyOf(refunds);
        finals = List.copyOf(finals);
        balances = List.copyOf(balances);
    }

    public int size() { return receipts.size() + refunds.size() + finals.size() + balances.size(); }

    /** Retained-source horizon, not an upstream no-events attestation. Do not publish earlier zeroes. */
    public void requireKnownHistory(LocalDate from, String reportZone) {
        var dates = new java.util.ArrayList<LocalDate>();
        receipts.forEach(fact -> dates.add(fact.businessDate()));
        refunds.forEach(fact -> dates.add(fact.businessDate()));
        finals.forEach(fact -> dates.add(fact.businessDate()));
        var zone = java.time.ZoneId.of(reportZone);
        balances.forEach(fact -> dates.add(fact.reconciledAt().atZone(zone).toLocalDate()));
        var earliest = dates.stream().min(LocalDate::compareTo);
        if (from == null || earliest.isEmpty() || from.isBefore(earliest.get())) {
            throw new ReportRuleException("REPORT_PUBLICATION_HISTORY_UNAVAILABLE", "Coverage cannot precede retained original source evidence");
        }
    }

    public List<FinancialSourceProjection.Day> project(LocalDate from, LocalDate to, String zone, Instant observedAt) {
        // An event claiming a future business completion cannot be hidden by the requested date range.
        if (observedAt == null || receipts.stream().anyMatch(fact -> fact.completedAt().isAfter(observedAt))
                || refunds.stream().anyMatch(fact -> fact.completedAt().isAfter(observedAt))
                || finals.stream().anyMatch(fact -> fact.completedAt().isAfter(observedAt))
                || balances.stream().anyMatch(fact -> fact.reconciledAt().isAfter(observedAt))) {
            throw new ReportRuleException("REPORT_FINANCIAL_SOURCE_CONFLICT", "Financial source follows its frozen observation");
        }
        return FinancialSourceProjection.rebuild(from, to, zone, receipts, refunds, finals, balances);
    }
}
