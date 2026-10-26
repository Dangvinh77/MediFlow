package com.mediflow.billing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** A quote may be replaced by a new ID; its issued lines and validity never change. */
public record OutpatientQuote(UUID quoteId, UUID patientId, String careEpisodeType, UUID careEpisodeId,
        List<OutpatientQuoteLine> lines, Instant issuedAt, Instant expiresAt) {
    public OutpatientQuote {
        if (quoteId == null || patientId == null || !"OUTPATIENT_VISIT".equals(careEpisodeType)
                || careEpisodeId == null || lines == null || lines.isEmpty() || lines.size() > 100
                || issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt))
            throw new IllegalArgumentException("Invalid outpatient quote identity or validity");
        lines = List.copyOf(lines);
        var keys = new HashSet<String>();
        for (var line : lines)
            if (!keys.add(line.sourceType() + ":" + line.sourceId() + ":" + line.priceCode()))
                throw new IllegalArgumentException("Duplicate quote source item");
    }
    public String currency() { return "VND"; }
    public BigDecimal grossAmount() {
        return lines.stream().map(OutpatientQuoteLine::grossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal supportAmount() {
        return lines.stream().map(OutpatientQuoteLine::supportAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal patientPayable() {
        return lines.stream().map(OutpatientQuoteLine::patientPayable).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public boolean requiresPatientPayment() { return patientPayable().signum() > 0; }
    public boolean isPayableAt(Instant at) {
        return at != null && !at.isBefore(issuedAt) && at.isBefore(expiresAt);
    }
}
