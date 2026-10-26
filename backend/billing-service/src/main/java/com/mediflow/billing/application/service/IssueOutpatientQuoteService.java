package com.mediflow.billing.application.service;

import com.mediflow.billing.application.port.in.LookupOutpatientQuoteUseCase;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.domain.model.OutpatientQuote;
import com.mediflow.billing.domain.model.OutpatientQuoteLine;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Called inside the request/charge issuance transaction, never from a human quote body. */
public class IssueOutpatientQuoteService implements LookupOutpatientQuoteUseCase {
    private final OutpatientQuoteRepositoryPort quotes;
    public IssueOutpatientQuoteService(OutpatientQuoteRepositoryPort quotes) { this.quotes = quotes; }
    public OutpatientQuote issue(UUID paymentRequestId, UUID patientId, UUID careEpisodeId,
            List<OutpatientQuoteLine> lines, Instant issuedAt, Instant expiresAt) {
        if (paymentRequestId == null) throw new IllegalArgumentException("Payment request is required");
        var quote = new OutpatientQuote(UUID.randomUUID(), patientId, "OUTPATIENT_VISIT", careEpisodeId,
                lines, issuedAt, expiresAt);
        quotes.append(paymentRequestId, quote);
        return quote;
    }
    @Override public Optional<OutpatientQuote> lookup(UUID paymentRequestId) { return quotes.findCurrent(paymentRequestId); }
}
