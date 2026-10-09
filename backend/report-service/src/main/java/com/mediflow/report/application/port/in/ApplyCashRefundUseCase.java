package com.mediflow.report.application.port.in;

import java.util.UUID;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
public interface ApplyCashRefundUseCase {
    void apply(DecodedCareFinanceEvent event);
    void recover(UUID originalTransactionId);
}
