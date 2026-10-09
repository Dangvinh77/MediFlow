package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;

/** Accepted revision-one operations or admission evidence; never financial publication. */
public interface ReceiveOperationalReportFactUseCase {
    void receive(DecodedCareFinanceEvent event);
}
