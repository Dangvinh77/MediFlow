package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.OutpatientCancellationCommand;

public interface ProcessOutpatientCancellationUseCase {
    void receive(OutpatientCancellationCommand command);
}
