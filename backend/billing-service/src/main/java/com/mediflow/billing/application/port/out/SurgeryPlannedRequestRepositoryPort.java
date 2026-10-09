package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.SurgeryChargeCommand;
import com.mediflow.billing.domain.model.Charge;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

public interface SurgeryPlannedRequestRepositoryPort {
    void claimDelivery(SurgeryChargeCommand command);
    Optional<UUID> lockRecordedSource(SurgeryChargeCommand command);
    UUID openAndLockExactAccount(SurgeryChargeCommand command);
    IssuedRequest saveChargesAndRequest(SurgeryChargeCommand command, UUID accountId, List<Charge> charges);
    record IssuedRequest(UUID paymentRequestId, UUID invoiceId, BigDecimal requestedAmount) { }
}
