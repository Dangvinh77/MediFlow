package com.mediflow.report.application.port.out;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.FinalSettlementRecognition;
import java.util.List;
import java.util.UUID;

public interface FinalSettlementRecognitionStorePort {
    void apply(DecodedCareFinanceEvent event,FinalSettlementRecognition fact);
    void recover(UUID operationId);
    List<UUID> pending(int limit);
    void defer(UUID operationId);
}
