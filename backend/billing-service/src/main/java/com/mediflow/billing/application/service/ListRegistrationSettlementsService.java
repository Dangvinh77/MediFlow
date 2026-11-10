package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.response.RegistrationSettlementDTO;
import com.mediflow.billing.application.port.in.ListRegistrationSettlementsUseCase;
import com.mediflow.billing.application.port.out.OutpatientRegistrationSourcePort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import java.util.Set;

public final class ListRegistrationSettlementsService implements ListRegistrationSettlementsUseCase {
    private static final Set<String> KINDS = Set.of("VOID_UNPAID", "REFUND_DUE", "RESCHEDULED");
    private final OutpatientRegistrationSourcePort sources;

    public ListRegistrationSettlementsService(OutpatientRegistrationSourcePort sources) { this.sources = sources; }

    @Override public PageResult<RegistrationSettlementDTO> list(String kind, PageQuery page) {
        if (kind != null && !KINDS.contains(kind)) throw new BillingRuleException("BILLING_SETTLEMENT_KIND_INVALID", "Unknown settlement kind");
        return sources.findSettlements(kind, PageQuery.of(page.page(), page.size()));
    }
}
