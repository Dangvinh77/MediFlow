package com.mediflow.billing.application.dto.response;

import com.mediflow.billing.domain.model.LabZeroPayableEligibilityReceipt;
import java.time.Instant;
import java.util.UUID;

public record LabZeroPayableEligibilityDTO(UUID eligibilityOperationId, UUID paymentRequestId,
        UUID quoteId, String basis, UUID clearanceId, Instant completedAt) {
    public static LabZeroPayableEligibilityDTO from(LabZeroPayableEligibilityReceipt receipt) {
        return new LabZeroPayableEligibilityDTO(receipt.eligibilityOperationId(), receipt.paymentRequestId(),
                receipt.quoteId(), "ZERO_LIABILITY", receipt.clearanceId(), receipt.completedAt());
    }
}
