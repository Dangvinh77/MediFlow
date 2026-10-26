package com.mediflow.billing.application.port.out;

import com.mediflow.billing.domain.model.LabZeroPayableEligibilityReceipt;
import com.mediflow.billing.domain.model.OutpatientQuote;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort.PaymentContext;
import java.util.Optional;
import java.util.UUID;

/** Local durable receipt and exact issuer-source checks for Lab zero-liability eligibility. */
public interface LabZeroPayableEligibilityRepositoryPort {
    void lockIdempotencyKey(String key);
    void lockLabSource(UUID paymentRequestId);
    Optional<LabZeroPayableEligibilityReceipt> findByIdempotencyKey(String key);
    Optional<LabZeroPayableEligibilityReceipt> findByPaymentRequestId(UUID paymentRequestId);
    void verifyExactLabBinding(UUID paymentRequestId, PaymentContext context, OutpatientQuote quote);
    void append(LabZeroPayableEligibilityReceipt receipt);
}
