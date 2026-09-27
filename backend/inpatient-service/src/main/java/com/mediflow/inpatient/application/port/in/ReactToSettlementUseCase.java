package com.mediflow.inpatient.application.port.in;

import com.mediflow.inpatient.application.dto.command.SettlementCompletedCommand;

public interface ReactToSettlementUseCase {
    void onSettlementCompleted(SettlementCompletedCommand command);
}
