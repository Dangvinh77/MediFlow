package com.mediflow.billing.application.port.in;

import com.mediflow.billing.domain.model.LabZeroPayableEligibilityReceipt;
import java.util.UUID;

public interface GrantLabZeroPayableEligibilityUseCase {
    LabZeroPayableEligibilityReceipt grant(UUID paymentRequestId, UUID quoteId, UUID actorAccountId,
            String idempotencyKey, String correlationId);
}
