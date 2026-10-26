package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.LabTestChargeCommand;
import com.mediflow.billing.application.port.out.LabTestChargeRepositoryPort;
import com.mediflow.billing.application.port.out.LabTestChargeRepositoryPort.IssuedRequest;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort.PriceSnapshot;
import com.mediflow.billing.application.port.out.OutpatientQuoteCatalogPort;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.domain.model.OutpatientQuote;
import com.mediflow.billing.domain.model.OutpatientQuoteLine;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LabTestPlannedRequestServiceTest {
    private final LabTestChargeRepositoryPort repository = mock(LabTestChargeRepositoryPort.class);
    private final PriceCatalogPort priceCatalog = mock(PriceCatalogPort.class);
    private final LabTestPlannedRequestService service = new LabTestPlannedRequestService(repository, priceCatalog);

    private final Instant now = Instant.parse("2026-09-28T06:00:00Z");
    private LabTestChargeCommand command;

    @BeforeEach
    void setUp() {
        command = new LabTestChargeCommand(UUID.randomUUID(), fingerprint(), fingerprint(), "corr-1",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "OUTPATIENT_VISIT", UUID.randomUUID(), UUID.randomUUID(), "LAB-CBC", "CBC", now, null);
    }

    @Test
    void issue_newSource_opensAccountAndSavesChargeAndRequest() {
        when(repository.lockRecordedSource(command)).thenReturn(Optional.empty());
        UUID account = UUID.randomUUID();
        when(repository.openAndLockExactAccount(command)).thenReturn(account);
        when(priceCatalog.requireActive("LAB-CBC", now)).thenReturn(new PriceSnapshot("Xét nghiệm CBC", new BigDecimal("150000.00")));
        UUID requestId = UUID.randomUUID();
        when(repository.saveChargeAndRequest(eq(command), eq(account), any(), eq(new BigDecimal("150000.00")), isNull()))
                .thenReturn(new IssuedRequest(requestId, UUID.randomUUID(), new BigDecimal("150000.00")));

        UUID result = service.issue(command);

        assertThat(result).isEqualTo(requestId);
        verify(repository).claimDelivery(command);
        verify(repository).saveChargeAndRequest(eq(command), eq(account), argThat(charge ->
                charge.getPriceCode().equals("LAB-CBC") && charge.getGrossAmount().compareTo(new BigDecimal("150000.00")) == 0),
                eq(new BigDecimal("150000.00")), isNull());
    }

    @Test
    void issue_exactReplay_returnsRecordedRequestWithoutRepricing() {
        UUID recordedRequest = UUID.randomUUID();
        when(repository.lockRecordedSource(command)).thenReturn(Optional.of(recordedRequest));

        UUID result = service.issue(command);

        assertThat(result).isEqualTo(recordedRequest);
        verifyNoInteractions(priceCatalog);
        verify(repository, never()).saveChargeAndRequest(any(), any(), any(), any(), any());
    }

    @Test
    void issue_unknownPriceCode_rejectsWithoutSaving() {
        when(repository.lockRecordedSource(command)).thenReturn(Optional.empty());
        when(repository.openAndLockExactAccount(command)).thenReturn(UUID.randomUUID());
        when(priceCatalog.requireActive("LAB-CBC", now))
                .thenThrow(new BillingRuleException("BILLING_PRICE_CODE_UNKNOWN", "unknown"));

        assertThatThrownBy(() -> service.issue(command)).isInstanceOf(BillingRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BILLING_PRICE_CODE_UNKNOWN");
        verify(repository, never()).saveChargeAndRequest(any(), any(), any(), any(), any());
    }

    @Test
    void quoteEnabled_outpatientUsesServerQuoteAndPersistsPatientPayableWithFifteenMinuteExpiry() {
        when(repository.lockRecordedSource(command)).thenReturn(Optional.empty());
        UUID account = UUID.randomUUID();
        when(repository.openAndLockExactAccount(command)).thenReturn(account);
        UUID requestId = UUID.randomUUID();
        var issuedAt = Instant.parse("2026-10-26T08:00:00Z");
        var expiresAt = issuedAt.plus(Duration.ofMinutes(15));
        var catalog = mock(OutpatientQuoteCatalogPort.class);
        var quoteRepository = mock(OutpatientQuoteRepositoryPort.class);
        var issuer = new IssueOutpatientQuoteService(quoteRepository);
        var line = OutpatientQuoteLine.calculate(command.labId(), "LAB_TEST", command.priceCode(),
                "CAT-1", "POLICY-1", "CBC", BigDecimal.ONE, new BigDecimal("100000"), BigDecimal.ONE);
        when(catalog.requireLine(command.labId(), "LAB_TEST", command.priceCode(), BigDecimal.ONE, issuedAt))
                .thenReturn(line);
        when(repository.saveChargeAndRequest(eq(command), eq(account), any(), eq(BigDecimal.ZERO), eq(expiresAt)))
                .thenReturn(new IssuedRequest(requestId, UUID.randomUUID(), BigDecimal.ZERO));
        var quoted = new LabTestPlannedRequestService(repository, priceCatalog, null, catalog, issuer, true,
                Clock.fixed(issuedAt, ZoneOffset.UTC));

        UUID result = quoted.issue(command);

        assertThat(result).isEqualTo(requestId);
        verify(priceCatalog, never()).requireActive(any(), any());
        verify(repository).saveChargeAndRequest(eq(command), eq(account), argThat(charge ->
                charge.getGrossAmount().compareTo(new BigDecimal("100000")) == 0), eq(BigDecimal.ZERO), eq(expiresAt));
        org.mockito.ArgumentCaptor<OutpatientQuote> quote = org.mockito.ArgumentCaptor.forClass(OutpatientQuote.class);
        verify(quoteRepository).append(eq(requestId), quote.capture());
        assertThat(quote.getValue().patientPayable()).isEqualByComparingTo("0");
        assertThat(quote.getValue().expiresAt()).isEqualTo(expiresAt);
        assertThat(quote.getValue().lines()).containsExactly(line);
    }

    private static String fingerprint() {
        return "a".repeat(64);
    }
}
