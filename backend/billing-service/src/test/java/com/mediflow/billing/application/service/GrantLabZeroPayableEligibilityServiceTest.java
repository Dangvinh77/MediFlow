package com.mediflow.billing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.port.out.LabZeroPayableEligibilityRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.domain.model.ClearanceTarget;
import com.mediflow.billing.domain.model.FinancialClearance;
import com.mediflow.billing.domain.model.LabZeroPayableEligibilityReceipt;
import com.mediflow.billing.domain.model.OutpatientQuote;
import com.mediflow.billing.domain.model.OutpatientQuoteLine;
import com.mediflow.billing.domain.model.PaymentRequest;
import com.mediflow.billing.domain.model.PaymentRequestPurpose;
import com.mediflow.billing.domain.model.PaymentRequestStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrantLabZeroPayableEligibilityServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");
    private final LedgerPaymentRepositoryPort ledger = mock(LedgerPaymentRepositoryPort.class);
    private final LabZeroPayableEligibilityRepositoryPort receipts = mock(LabZeroPayableEligibilityRepositoryPort.class);
    private final OutpatientQuoteRepositoryPort quotes = mock(OutpatientQuoteRepositoryPort.class);
    private final LedgerEventPort events = mock(LedgerEventPort.class);
    private final GrantLabZeroPayableEligibilityService service = new GrantLabZeroPayableEligibilityService(
            ledger, receipts, quotes, events, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID requestId = UUID.randomUUID();
    private final UUID quoteId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();
    private final UUID recordId = episodeId;
    private final UUID labId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();
    private final Instant expiry = NOW.plusSeconds(600);
    private BillingAccount account;
    private PaymentRequest request;
    private LedgerPaymentRepositoryPort.PaymentContext context;
    private OutpatientQuote quote;

    @BeforeEach void setUp() {
        account = BillingAccount.restore(accountId, patientId, departmentId, CareEpisodeType.OUTPATIENT_VISIT,
                episodeId, AccountStatus.OPEN, "VND", 0, NOW, null, null, NOW, NOW);
        request = PaymentRequest.restore(requestId, invoiceId, accountId, PaymentRequestPurpose.LAB_TEST,
                PaymentRequestStatus.PENDING, BigDecimal.ZERO, "VND", expiry, null, NOW, null);
        context = new LedgerPaymentRepositoryPort.PaymentContext(account, request,
                new ClearanceTarget(null, recordId, List.of(labId), null, null, null), BigDecimal.ZERO);
        var line = OutpatientQuoteLine.calculate(labId, "LAB_TEST", "LAB-CBC", "CAT-1", "POLICY-1",
                "Complete blood count", BigDecimal.ONE, new BigDecimal("100000"), BigDecimal.ONE);
        quote = new OutpatientQuote(quoteId, patientId, "OUTPATIENT_VISIT", episodeId, List.of(line), NOW.minusSeconds(1), expiry);
        when(receipts.findByIdempotencyKey("lab-zero-1")).thenReturn(Optional.empty());
        when(receipts.findByPaymentRequestId(requestId)).thenReturn(Optional.empty());
        when(ledger.lockRequest(requestId)).thenReturn(context);
        when(quotes.findCurrent(requestId)).thenReturn(Optional.of(quote));
        doNothing().when(receipts).verifyExactLabBinding(requestId, context, quote);
        when(ledger.saveClearance(any())).thenAnswer(invocation -> {
            FinancialClearance value = invocation.getArgument(0);
            return FinancialClearance.restore(UUID.randomUUID(), value.getAccountId(), value.getPaymentRequestId(),
                    value.getInvoiceId(), value.getPatientId(), value.getPurpose(), value.getCareEpisodeType(),
                    value.getCareEpisodeId(), value.getAppointmentId(), value.getRecordId(), value.getLabTestIds(),
                    value.getPrescriptionId(), value.getAdmissionId(), value.getSurgeryCaseId(), value.getAmount(),
                    value.getCurrency(), value.getPaymentMethod(), value.isEmergencyOverride(), value.getExpiresAt(),
                    value.getGrantedAt(), null);
        });
    }

    @Test void zeroQuoteCreatesClearanceAndReceiptWithoutPaymentTransactionAndExactReplayReusesReceipt() {
        var first = service.grant(requestId, quoteId, actorId, "lab-zero-1", "corr-1");
        when(receipts.findByIdempotencyKey("lab-zero-1")).thenReturn(Optional.of(first));

        var replay = service.grant(requestId, quoteId, actorId, "lab-zero-1", "corr-retry");

        assertThat(replay).isSameAs(first);
        assertThat(first.quoteId()).isEqualTo(quoteId);
        assertThat(first.clearanceId()).isNotNull();
        assertThat(first.completedAt()).isEqualTo(NOW);
        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.PAID);
        verify(ledger).saveZeroLiability(request, actorId);
        verify(ledger, never()).save(any(), any(), any());
        verify(ledger, never()).allocate(any());
        verify(events).appendForOutpatientRequest(eq(accountId), eq(requestId), any(LedgerIntegrationEvent.class));
        verify(receipts).append(any(LabZeroPayableEligibilityReceipt.class));
    }

    @Test void nonZeroQuoteCannotGrantAZeroLiabilityClearance() {
        var line = OutpatientQuoteLine.calculate(labId, "LAB_TEST", "LAB-CBC", "CAT-1", "POLICY-1",
                "Complete blood count", BigDecimal.ONE, new BigDecimal("100000"), new BigDecimal("0.5"));
        when(quotes.findCurrent(requestId)).thenReturn(Optional.of(new OutpatientQuote(quoteId, patientId,
                "OUTPATIENT_VISIT", episodeId, List.of(line), NOW.minusSeconds(1), expiry)));

        assertThatThrownBy(() -> service.grant(requestId, quoteId, actorId, "lab-zero-1", "corr-1"))
                .isInstanceOf(BillingRuleException.class).hasFieldOrPropertyWithValue("code", "BILLING_ZERO_ELIGIBILITY_INVALID");
        verify(ledger, never()).saveZeroLiability(any(), any());
        verify(events, never()).appendForOutpatientRequest(any(), any(), any());
        verify(receipts, never()).append(any());
    }
}
