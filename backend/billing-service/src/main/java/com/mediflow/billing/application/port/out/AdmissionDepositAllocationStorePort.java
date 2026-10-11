package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.AllocateAdmissionDepositCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.DepositAllocatedPayload;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionDepositAllocationStorePort {
    Optional<DepositAllocatedPayload> lockAndFindReplay(AllocateAdmissionDepositCommand command);
    void lockMedicallyClosedAccount(UUID accountId);
    void save(AllocateAdmissionDepositCommand command, DepositAllocatedPayload payload);
}
