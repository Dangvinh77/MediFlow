package com.mediflow.billing.application.port.in;

import java.util.UUID;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.dto.response.LedgerPaymentDTO;

public interface ProcessLedgerPaymentUseCase {
    LedgerPaymentDTO complete(UUID requestId, CompleteLedgerPaymentRequest command,
                              UUID actorAccountId, String correlationId);
}
