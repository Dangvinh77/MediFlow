package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort.PaymentContext;
import com.mediflow.billing.domain.model.LabQuotedPaymentReceipt;
import com.mediflow.billing.domain.model.OutpatientQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Local Lab binding and append-only payment evidence for quote-bound V2 cash payments. */
public interface LabQuotedPaymentRepositoryPort {
    boolean isQuoteBoundLabRequest(UUID paymentRequestId);
    void lockLabSource(UUID paymentRequestId);
    OutpatientQuote requireCurrentPayableQuote(UUID paymentRequestId, PaymentContext context, UUID quoteId,
                                               BigDecimal amount, Instant now);
    Optional<LabQuotedPaymentReceipt> findByIdempotencyKey(String idempotencyKey);
    Optional<LabQuotedPaymentReceipt> findByPaymentRequestId(UUID paymentRequestId);
    UUID requireClearanceId(UUID paymentRequestId);
    void append(LabQuotedPaymentReceipt receipt);
}
