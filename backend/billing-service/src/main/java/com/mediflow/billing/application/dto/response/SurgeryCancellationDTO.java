package com.mediflow.billing.application.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SurgeryCancellationDTO(UUID surgeryCaseId, UUID cancellationId, String status,
        String rejectionCode, List<RefundDue> refundsDue) {
    public SurgeryCancellationDTO { refundsDue = List.copyOf(refundsDue); }
    public record RefundDue(UUID originalTransactionId, BigDecimal amount, String currency) { }
}
