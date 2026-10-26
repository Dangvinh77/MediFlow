package com.mediflow.billing.application.port.out;

import com.mediflow.billing.domain.model.OutpatientQuote;
import java.util.Optional;
import java.util.UUID;

public interface OutpatientQuoteRepositoryPort {
    void append(UUID paymentRequestId, OutpatientQuote quote);
    Optional<OutpatientQuote> findCurrent(UUID paymentRequestId);
}
