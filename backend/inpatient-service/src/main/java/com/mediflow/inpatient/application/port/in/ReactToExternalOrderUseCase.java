package com.mediflow.inpatient.application.port.in;

import com.mediflow.inpatient.application.dto.command.ExternalOrderFactCommand;

public interface ReactToExternalOrderUseCase {
    void onExternalOrderFact(ExternalOrderFactCommand command);
}
