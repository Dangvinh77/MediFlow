package com.mediflow.report.application.mapper;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.mediflow.report.domain.model.CashReceipt;

/** Shared live/replay effects: gross receipts only; no FX conversion or inferred recognition. */
public final class CashProjectionPlanner {
    private CashProjectionPlanner() { }

    public record Scope(CashReceipt receipt, UUID departmentId) { }

    public static List<Scope> scopes(List<CashReceipt> receipts) {
        return receipts.stream().flatMap(receipt -> Stream.of(new Scope(receipt, null),
                new Scope(receipt, receipt.departmentId()))).sorted(Comparator
                .comparing((Scope scope) -> scope.receipt().businessDate())
                .thenComparing(scope -> scope.receipt().currency())
                .thenComparing(scope -> scope.receipt().reportZone())
                .thenComparing(scope -> scope.receipt().classification())
                .thenComparing(Scope::departmentId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(scope -> scope.receipt().transactionId())).toList();
    }
}
