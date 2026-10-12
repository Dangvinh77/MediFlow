package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.ExamChargeCommand;
import com.mediflow.billing.application.port.out.ExamChargeRepositoryPort;
import com.mediflow.billing.application.port.out.ExamChargeRepositoryPort.IssuedRequest;
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

class ExamChargePlannedRequestServiceTest {
    private final ExamChargeRepositoryPort repository = mock(ExamChargeRepositoryPort.class);
    private final PriceCatalogPort priceCatalog = mock(PriceCatalogPort.class);
    private final ExamChargePlannedRequestService service = new ExamChargePlannedRequestService(repository, priceCatalog);

    private final Instant now = Instant.parse("2026-10-10T09:00:00Z");
    private ExamChargeCommand command;
    private UUID appointmentId;

    @BeforeEach
    void setUp() {
        appointmentId = UUID.randomUUID();
        command = new ExamChargeCommand(UUID.randomUUID(), fingerprint(), fingerprint(), "corr-1",
                appointmentId, UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT_VISIT", appointmentId,
                "OUTPATIENT_EXAM", now);
    }

    @Test
    void issue_newSource_opensAccountAndSavesChargeAndRequest() {
        when(repository.lockRecordedSource(command)).thenReturn(Optional.empty());
        UUID account = UUID.randomUUID();
        when(repository.openAndLockExactAccount(command)).thenReturn(account);
        when(priceCatalog.requireActive("OUTPATIENT_EXAM", now))
                .thenReturn(new PriceSnapshot("Khám ngoại trú", new BigDecimal("100000.00")));
        UUID requestId = UUID.randomUUID();
        when(repository.saveChargeAndRequest(eq(command), eq(account), any()))
                .thenReturn(new IssuedRequest(requestId, UUID.randomUUID(), new BigDecimal("100000.00")));

        UUID result = service.issue(command);

        assertThat(result).isEqualTo(requestId);
        verify(repository).claimDelivery(command);
        verify(repository).saveChargeAndRequest(eq(command), eq(account), argThat(charge ->
                charge.getPriceCode().equals("OUTPATIENT_EXAM")
                        && charge.getGrossAmount().compareTo(new BigDecimal("100000.00")) == 0));
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
        when(priceCatalog.requireActive("OUTPATIENT_EXAM", now))
                .thenThrow(new BillingRuleException("BILLING_PRICE_CODE_UNKNOWN", "unknown"));

        assertThatThrownBy(() -> service.issue(command)).isInstanceOf(BillingRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BILLING_PRICE_CODE_UNKNOWN");
        verify(repository, never()).saveChargeAndRequest(any(), any(), any());
    }

    private static String fingerprint() {
        return "a".repeat(64);
    }
}
