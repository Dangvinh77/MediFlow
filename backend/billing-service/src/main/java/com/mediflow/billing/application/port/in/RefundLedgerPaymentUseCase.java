package com.mediflow.billing.application.port.in;

import java.util.UUID;
import com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest;
import com.mediflow.billing.application.dto.response.LedgerRefundDTO;

public interface RefundLedgerPaymentUseCase {
    LedgerRefundDTO refund(UUID originalTransactionId, RefundLedgerPaymentRequest command,
                            UUID actorAccountId, String correlationId);
}
