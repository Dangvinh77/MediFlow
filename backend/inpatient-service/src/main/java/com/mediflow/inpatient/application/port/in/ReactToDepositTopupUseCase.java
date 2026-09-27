package com.mediflow.inpatient.application.port.in;

import com.mediflow.inpatient.application.dto.command.DepositTopupRequiredCommand;

public interface ReactToDepositTopupUseCase {
    void onDepositTopupRequired(DepositTopupRequiredCommand command);
}
