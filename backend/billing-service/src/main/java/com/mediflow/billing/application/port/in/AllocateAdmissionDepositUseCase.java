package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.command.AllocateAdmissionDepositCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.DepositAllocatedPayload;

public interface AllocateAdmissionDepositUseCase {
    DepositAllocatedPayload allocate(AllocateAdmissionDepositCommand command);
}
