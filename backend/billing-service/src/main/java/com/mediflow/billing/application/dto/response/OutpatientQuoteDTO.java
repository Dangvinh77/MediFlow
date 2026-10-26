package com.mediflow.billing.application.dto.response;

import com.mediflow.billing.domain.model.OutpatientQuote;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OutpatientQuoteDTO(UUID quoteId, UUID paymentRequestId, UUID patientId, String careEpisodeType,
        UUID careEpisodeId, String currency, List<Line> lines, String grossAmount, String supportAmount,
        String patientPayable, Instant issuedAt, Instant expiresAt) {
    public static OutpatientQuoteDTO from(UUID paymentRequestId, OutpatientQuote q) {
        return new OutpatientQuoteDTO(q.quoteId(), paymentRequestId, q.patientId(), q.careEpisodeType(), q.careEpisodeId(),
                q.currency(), q.lines().stream().map(l -> new Line(l.sourceId(), l.sourceType(), l.priceCode(),
                        l.catalogueVersion(), l.policyVersion(), l.description(), l.quantity().toPlainString(), l.unitPrice().toPlainString(),
                        l.grossAmount().toPlainString(), l.supportRate().toPlainString(), l.supportAmount().toPlainString(), l.patientPayable().toPlainString())).toList(),
                q.grossAmount().toPlainString(), q.supportAmount().toPlainString(), q.patientPayable().toPlainString(), q.issuedAt(), q.expiresAt());
    }
    public record Line(UUID sourceId, String sourceType, String priceCode, String catalogueVersion, String policyVersion,
            String description, String quantity, String unitPrice, String grossAmount, String supportRate, String supportAmount, String patientPayable) {}
}
