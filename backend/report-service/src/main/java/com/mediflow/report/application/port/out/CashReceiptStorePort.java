package com.mediflow.report.application.port.out;

import java.util.UUID;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.CashReceipt;

public interface CashReceiptStorePort {
    /** Verify delivery/source evidence atomically; return true only for a new immutable transaction. */
    boolean record(DecodedCareFinanceEvent event, CashReceipt receipt);
    void incrementGrossReceipts(CashReceipt receipt, UUID departmentId);
}
