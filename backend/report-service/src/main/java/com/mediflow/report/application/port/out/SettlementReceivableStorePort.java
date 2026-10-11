package com.mediflow.report.application.port.out;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.SettlementReceivableSnapshot;

public interface SettlementReceivableStorePort {
    void apply(DecodedCareFinanceEvent event, SettlementReceivableSnapshot snapshot);
}
