package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.PrescriptionChargeCommand;
import com.mediflow.billing.application.port.out.PrescriptionChargeRepositoryPort.IssuedCharge;

public interface IssuePrescriptionChargeUseCase {
    IssuedCharge issue(PrescriptionChargeCommand command);
}
