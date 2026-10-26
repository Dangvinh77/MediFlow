package com.mediflow.billing.application.port.in;

import com.mediflow.billing.domain.model.OutpatientQuote;
import java.util.Optional;
import java.util.UUID;

public interface LookupOutpatientQuoteUseCase {
    Optional<OutpatientQuote> lookup(UUID paymentRequestId);
}
