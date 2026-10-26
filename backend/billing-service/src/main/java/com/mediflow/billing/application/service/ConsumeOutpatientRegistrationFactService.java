package com.mediflow.billing.application.service;

import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;
import com.mediflow.billing.application.port.in.ConsumeOutpatientRegistrationFactUseCase;
import com.mediflow.billing.application.port.out.OutpatientQuoteCatalogPort;
import com.mediflow.billing.application.port.out.OutpatientRegistrationSourcePort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.OutpatientQuoteLine;
import com.mediflow.billing.application.port.out.PriceCatalogPort.PriceSnapshot;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class ConsumeOutpatientRegistrationFactService implements ConsumeOutpatientRegistrationFactUseCase {
    private final OutpatientRegistrationSourcePort sources;
    private final PriceCatalogPort prices;
    private final OutpatientQuoteCatalogPort quotes;
    private final boolean quotesEnabled;

    public ConsumeOutpatientRegistrationFactService(OutpatientRegistrationSourcePort sources, PriceCatalogPort prices,
            OutpatientQuoteCatalogPort quotes, boolean quotesEnabled) {
        this.sources = sources;
        this.prices = prices;
        this.quotes = quotes;
        this.quotesEnabled = quotesEnabled;
    }

    @Override
    public void consume(OutpatientRegistrationChangedEvent event, byte[] rawEnvelope) {
        validate(event, rawEnvelope);
        var fact = event.payload();
        PriceSnapshot price = null;
        OutpatientQuoteLine quoteLine = null;
        if ("AWAITING_PAYMENT".equals(fact.status()) && sources.requiresPrice(fact.registrationId())) {
            try {
                if (quotesEnabled) {
                    if (quotes == null) throw new IllegalStateException("Outpatient quote catalog is not configured");
                    quoteLine = quotes.requireLine(fact.registrationId(), fact.sourceType(), fact.priceCode(),
                            BigDecimal.ONE, fact.changedAt());
                } else {
                    price = prices.requireActive(fact.priceCode(), fact.changedAt());
                    if (price != null && (price.unitAmount() == null || price.unitAmount().signum() <= 0
                            || price.unitAmount().stripTrailingZeros().scale() > 2
                            || price.unitAmount().precision() - price.unitAmount().scale() > 17)) {
                        price = null;
                    }
                }
            } catch (BillingRuleException unavailable) {
                if (!"BILLING_PRICE_CODE_UNKNOWN".equals(unavailable.getCode())) throw unavailable;
            }
        }
        sources.apply(event, rawEnvelope, fingerprint(event), price, quoteLine);
    }

    private static void validate(OutpatientRegistrationChangedEvent event, byte[] rawEnvelope) {
        if (event == null || rawEnvelope == null || rawEnvelope.length == 0 || event.eventId() == null
                || !"outpatient.registration.changed".equals(event.eventType()) || event.version() != 1
                || event.occurredAt() == null || event.correlationId() == null || event.correlationId().isBlank()
                || event.correlationId().length() > 120 || !"clinical-service".equals(event.producer())
                || event.payload() == null) fail();
        var fact = event.payload();
        if (fact.registrationId() == null || fact.registrationRevision() <= 0 || fact.patientId() == null
                || fact.doctorId() == null || fact.departmentId() == null || fact.sessionId() == null
                || fact.roomId() == null || fact.sessionDate() == null || fact.startTime() == null
                || fact.endTime() == null || fact.visitType() == null || fact.reservationId() == null
                || fact.reservationStatus() == null || fact.status() == null || fact.reservationExpiresAt() == null
                || fact.priceCode() == null || fact.priceCode().isBlank() || fact.priceCode().length() > 64
                || !"EXAM".equals(fact.sourceType()) || !fact.registrationId().equals(fact.sourceId())
                || !"OUTPATIENT_VISIT".equals(fact.careEpisodeType())
                || !fact.registrationId().equals(fact.careEpisodeId()) || fact.changedAt() == null) fail();
        if ("CANCELLED".equals(fact.status())) {
            var cancellation = fact.cancellation();
            if (cancellation == null || cancellation.actorAccountId() == null || cancellation.reason() == null
                    || cancellation.reason().isBlank() || cancellation.reason().length() > 500
                    || cancellation.cancelledAt() == null) fail();
        } else if (fact.cancellation() != null) fail();
        if ("AWAITING_PAYMENT".equals(fact.status()) && fact.queueNumber() != null) fail();
        if ("CONFIRMED".equals(fact.status()) && (fact.queueNumber() == null || fact.queueNumber() < 1)) fail();
        if (!UUID.fromString(fact.registrationId().toString()).equals(fact.registrationId())) fail();
    }

    private static String fingerprint(OutpatientRegistrationChangedEvent event) {
        var fact = event.payload();
        String value = String.join("\n", fact.registrationId().toString(), Long.toString(fact.registrationRevision()),
                fact.patientId().toString(), fact.doctorId().toString(), fact.departmentId().toString(),
                fact.sessionId().toString(), fact.roomId().toString(), fact.sessionDate().toString(),
                fact.startTime().toString(), fact.endTime().toString(), fact.visitType(), fact.reservationId().toString(),
                fact.reservationStatus(), fact.status(), String.valueOf(fact.checkedInAt()), String.valueOf(fact.queueNumber()),
                fact.reservationExpiresAt().toString(), fact.priceCode(), fact.sourceType(), fact.sourceId().toString(),
                fact.careEpisodeType(), fact.careEpisodeId().toString(), String.valueOf(fact.cancellation()),
                fact.changedAt().toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    private static void fail() {
        throw new BillingRuleException("BILLING_REGISTRATION_FACT_INVALID", "Registration fact is invalid");
    }
}
