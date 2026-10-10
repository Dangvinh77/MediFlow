package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.LabTestChargeCommand;
import com.mediflow.billing.domain.model.Charge;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface LabTestChargeRepositoryPort {
    void claimDelivery(LabTestChargeCommand command);
    Optional<UUID> lockRecordedSource(LabTestChargeCommand command);
    UUID openAndLockExactAccount(LabTestChargeCommand command);
    IssuedRequest saveChargeAndRequest(LabTestChargeCommand command, UUID accountId, Charge charge);
    record IssuedRequest(UUID paymentRequestId, UUID invoiceId, BigDecimal requestedAmount) { }
}
