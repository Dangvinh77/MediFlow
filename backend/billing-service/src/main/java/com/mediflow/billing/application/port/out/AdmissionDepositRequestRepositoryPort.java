package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.AdmissionDepositRequestCommand;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionDepositRequestRepositoryPort {
    void claimDelivery(AdmissionDepositRequestCommand command);
    Optional<UUID> lockRecordedSource(AdmissionDepositRequestCommand command);
    UUID openAndLockExactAccount(AdmissionDepositRequestCommand command);
    UUID saveRequest(AdmissionDepositRequestCommand command, UUID accountId);
}
