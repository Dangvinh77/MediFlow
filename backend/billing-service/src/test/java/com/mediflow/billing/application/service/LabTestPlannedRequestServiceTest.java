package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.LabTestChargeCommand;
import com.mediflow.billing.application.port.out.LabTestChargeRepositoryPort;
import com.mediflow.billing.application.port.out.LabTestChargeRepositoryPort.IssuedRequest;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort.PriceSnapshot;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
        when(repository.saveChargeAndRequest(eq(command), eq(account), any()))
                .thenReturn(new IssuedRequest(requestId, UUID.randomUUID(), new BigDecimal("150000.00")));

        UUID result = service.issue(command);

        assertThat(result).isEqualTo(requestId);
        verify(repository).claimDelivery(command);
        verify(repository).saveChargeAndRequest(eq(command), eq(account), argThat(charge ->
                charge.getPriceCode().equals("LAB-CBC") && charge.getGrossAmount().compareTo(new BigDecimal("150000.00")) == 0));
    }

    @Test
    void issue_exactReplay_returnsRecordedRequestWithoutRepricing() {
        UUID recordedRequest = UUID.randomUUID();
        when(repository.lockRecordedSource(command)).thenReturn(Optional.of(recordedRequest));

        UUID result = service.issue(command);

        assertThat(result).isEqualTo(recordedRequest);
        verifyNoInteractions(priceCatalog);
        verify(repository, never()).saveChargeAndRequest(any(), any(), any());
    }

    @Test
    void issue_unknownPriceCode_rejectsWithoutSaving() {
        when(repository.lockRecordedSource(command)).thenReturn(Optional.empty());
        when(repository.openAndLockExactAccount(command)).thenReturn(UUID.randomUUID());
        when(priceCatalog.requireActive("LAB-CBC", now))
                .thenThrow(new BillingRuleException("BILLING_PRICE_CODE_UNKNOWN", "unknown"));

        assertThatThrownBy(() -> service.issue(command)).isInstanceOf(BillingRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BILLING_PRICE_CODE_UNKNOWN");
        verify(repository, never()).saveChargeAndRequest(any(), any(), any());
    }

    private static String fingerprint() {
        return "a".repeat(64);
    }
}
