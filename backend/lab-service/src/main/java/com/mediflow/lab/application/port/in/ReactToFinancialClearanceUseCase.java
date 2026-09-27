package com.mediflow.lab.application.port.in;

import com.mediflow.lab.application.dto.command.FinancialClearanceCommand;

/** Applies Billing's purpose-scoped clearance to the explicitly named Lab tests. */
public interface ReactToFinancialClearanceUseCase {

    void onFinancialClearance(FinancialClearanceCommand command);
}
