package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.ExamChargeCommand;
import com.mediflow.billing.domain.model.Charge;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ExamChargeRepositoryPort {
    void claimDelivery(ExamChargeCommand command);
    Optional<UUID> lockRecordedSource(ExamChargeCommand command);
    UUID openAndLockExactAccount(ExamChargeCommand command);
    IssuedRequest saveChargeAndRequest(ExamChargeCommand command, UUID accountId, Charge charge);
    record IssuedRequest(UUID paymentRequestId, UUID invoiceId, BigDecimal requestedAmount) { }
}
