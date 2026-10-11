package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.BillingSettlementSnapshotMapper;
import com.mediflow.report.application.port.in.ApplySettlementSnapshotUseCase;
import com.mediflow.report.application.port.out.SettlementReceivableStorePort;
import org.springframework.transaction.annotation.Transactional;

public class SettlementSnapshotApplicationService implements ApplySettlementSnapshotUseCase {
    private final BillingSettlementSnapshotMapper mapper;
    private final SettlementReceivableStorePort store;
    public SettlementSnapshotApplicationService(BillingSettlementSnapshotMapper mapper, SettlementReceivableStorePort store) {
        this.mapper = mapper; this.store = store;
    }
    @Override @Transactional public void apply(DecodedCareFinanceEvent event) { store.apply(event, mapper.map(event)); }
}
