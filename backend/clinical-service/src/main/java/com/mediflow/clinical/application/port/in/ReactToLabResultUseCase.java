package com.mediflow.clinical.application.port.in;

import com.mediflow.clinical.application.dto.command.LabResultCreatedCommand;

public interface ReactToLabResultUseCase {

    void onLabResultCreated(LabResultCreatedCommand command);
}
