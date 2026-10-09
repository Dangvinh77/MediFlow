package com.mediflow.report.application.mapper;

import java.util.List;
import java.util.UUID;
import com.mediflow.report.domain.model.CashRefund;

/** Both live and replay cash-out use refund day and hospital-first original department scopes. */
public final class CashRefundProjectionPlanner {
    private CashRefundProjectionPlanner() { }
    public static List<Scope> scopes(CashRefund refund) {
        return List.of(new Scope(null), new Scope(refund.departmentId()));
    }
    public record Scope(UUID departmentId) { }
}
