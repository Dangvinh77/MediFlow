package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.CloseAdmissionChargesCommand;

public interface CloseAdmissionChargesUseCase {
    void close(CloseAdmissionChargesCommand command);
}
