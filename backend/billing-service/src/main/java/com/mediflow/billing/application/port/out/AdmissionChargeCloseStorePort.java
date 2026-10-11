package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.CloseAdmissionChargesCommand;

public interface AdmissionChargeCloseStorePort {
    void acceptAndClose(CloseAdmissionChargesCommand command);
}
