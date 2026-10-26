package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.request.LabQuotedPaymentRequest;
import com.mediflow.billing.application.dto.response.LedgerPaymentDTO;
import java.util.UUID;

public interface ProcessLabQuotedPaymentUseCase {
    LedgerPaymentDTO complete(UUID paymentRequestId, LabQuotedPaymentRequest command, String idempotencyKey,
                              UUID actorAccountId, String correlationId);
}
