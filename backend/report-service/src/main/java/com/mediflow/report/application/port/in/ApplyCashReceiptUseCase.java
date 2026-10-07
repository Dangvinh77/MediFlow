package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;

/** Internal/offline only. No Rabbit listener or HTTP command exposes this use case. */
public interface ApplyCashReceiptUseCase {
    void apply(DecodedCareFinanceEvent event);
}
