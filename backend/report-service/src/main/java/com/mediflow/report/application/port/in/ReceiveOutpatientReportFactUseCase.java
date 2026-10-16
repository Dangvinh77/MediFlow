package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;

/** Selected MVP intake, never the mixed admission/surgery projection pipeline. */
public interface ReceiveOutpatientReportFactUseCase {
    void receive(DecodedCareFinanceEvent event);
}
