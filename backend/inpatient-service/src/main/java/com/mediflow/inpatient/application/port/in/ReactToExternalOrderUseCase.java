package com.mediflow.inpatient.application.port.in;

import com.mediflow.inpatient.application.dto.command.ExternalOrderFactCommand;
import com.mediflow.inpatient.application.dto.command.SurgeryCaseCreatedCommand;

public interface ReactToExternalOrderUseCase {
    void onSurgeryCaseCreated(SurgeryCaseCreatedCommand command);
    void onExternalOrderFact(ExternalOrderFactCommand command);
}
