package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.BillingFinalSettlementRecognitionMapper;
import com.mediflow.report.application.port.in.ApplyFinalSettlementRecognitionUseCase;
import com.mediflow.report.application.port.out.FinalSettlementRecognitionStorePort;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class FinalSettlementRecognitionApplicationService implements ApplyFinalSettlementRecognitionUseCase {
    private final BillingFinalSettlementRecognitionMapper mapper;
    private final FinalSettlementRecognitionStorePort store;
    public FinalSettlementRecognitionApplicationService(BillingFinalSettlementRecognitionMapper mapper,FinalSettlementRecognitionStorePort store) {
        this.mapper=mapper;this.store=store;
    }
    @Override @Transactional(timeout=8) public void apply(DecodedCareFinanceEvent event){store.apply(event,mapper.map(event));}
    @Override @Transactional(timeout=8) public void recover(UUID operationId){store.recover(operationId);}
}
