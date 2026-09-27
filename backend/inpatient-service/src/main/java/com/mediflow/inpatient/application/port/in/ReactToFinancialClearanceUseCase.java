package com.mediflow.inpatient.application.port.in;

import com.mediflow.inpatient.application.dto.command.FinancialClearanceCommand;

public interface ReactToFinancialClearanceUseCase {
    void onFinancialClearance(FinancialClearanceCommand command);
}
