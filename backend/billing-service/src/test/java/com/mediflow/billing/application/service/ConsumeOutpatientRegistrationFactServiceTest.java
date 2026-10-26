package com.mediflow.billing.application.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;
import com.mediflow.billing.application.port.out.OutpatientQuoteCatalogPort;
import com.mediflow.billing.application.port.out.OutpatientRegistrationSourcePort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.domain.model.OutpatientQuoteLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsumeOutpatientRegistrationFactServiceTest {
    @Mock OutpatientRegistrationSourcePort sources;
    @Mock PriceCatalogPort prices;
    @Mock OutpatientQuoteCatalogPort quoteCatalog;

    @Test void quoteEnabledRegistrationUsesServerQuoteSnapshotAndDoesNotUseLegacyGrossPrice() {
        UUID registrationId = UUID.randomUUID();
        Instant changedAt = Instant.parse("2026-10-26T01:00:00Z");
        var event = event(registrationId, changedAt);
        byte[] raw = "unchanged-envelope".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var line = OutpatientQuoteLine.calculate(registrationId, "EXAM", "OUTPATIENT_EXAM",
                "catalogue-v1", "policy-v1", "Outpatient visit", BigDecimal.ONE,
                new BigDecimal("100000"), new BigDecimal("0.5"));
        when(sources.requiresPrice(registrationId)).thenReturn(true);
        when(quoteCatalog.requireLine(registrationId, "EXAM", "OUTPATIENT_EXAM", BigDecimal.ONE, changedAt))
                .thenReturn(line);

        new ConsumeOutpatientRegistrationFactService(sources, prices, quoteCatalog, true).consume(event, raw);

        verify(quoteCatalog).requireLine(registrationId, "EXAM", "OUTPATIENT_EXAM", BigDecimal.ONE, changedAt);
        verify(sources).apply(eq(event), aryEq(raw), anyString(), isNull(), eq(line));
        verifyNoInteractions(prices);
    }

    private static OutpatientRegistrationChangedEvent event(UUID registrationId, Instant changedAt) {
        var fact = new OutpatientRegistrationChangedEvent.RegistrationFact(registrationId, 1,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 26), LocalTime.of(9, 0), LocalTime.of(9, 30), "NEW_VISIT",
                UUID.randomUUID(), "HELD", "AWAITING_PAYMENT", null, null, changedAt.plusSeconds(600),
                "OUTPATIENT_EXAM", "EXAM", registrationId, "OUTPATIENT_VISIT", registrationId, null, changedAt);
        return new OutpatientRegistrationChangedEvent(UUID.randomUUID(), "outpatient.registration.changed", 1,
                changedAt, "trace", "clinical-service", fact);
    }
}
