package com.mediflow.clinical.application.port.in;

import com.mediflow.clinical.application.dto.command.FinancialClearanceCommand;

public interface ReactToFinancialClearanceUseCase {
    void onFinancialClearance(FinancialClearanceCommand command);
}
