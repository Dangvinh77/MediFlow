package com.mediflow.billing.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mapstruct.factory.Mappers;

import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort.PaymentContext;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort.RecordedPayment;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.*;

class LedgerPaymentServiceTest {
    private final Instant now = Instant.parse("2026-10-05T08:00:00Z");
    private final UUID requestId = UUID.randomUUID(), actor = UUID.randomUUID(), episode = UUID.randomUUID();
    private final LedgerPaymentRepositoryPort repository = mock(LedgerPaymentRepositoryPort.class);
    private final LedgerEventPort events = mock(LedgerEventPort.class);
    private LedgerPaymentService service;
    private BillingAccount account;
    private PaymentRequest request;
    private ClearanceTarget target;

    @BeforeEach void setup() {
        service = new LedgerPaymentService(repository, events, Mappers.getMapper(LedgerPaymentMapper.class),
                Clock.fixed(now, ZoneOffset.UTC));
        account = BillingAccount.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CareEpisodeType.OUTPATIENT_VISIT,
                episode, AccountStatus.OPEN, "VND", 0, now, null, null, now, now);
        request = PaymentRequest.restore(requestId, UUID.randomUUID(), account.getAccountId(), PaymentRequestPurpose.PRESCRIPTION,
                PaymentRequestStatus.PENDING, new BigDecimal("100"), "VND", now.plusSeconds(100), actor, now, null);
        target = new ClearanceTarget(null, null, List.of(), UUID.randomUUID(), null, null);
        when(repository.lockRequest(requestId)).thenAnswer(call -> new PaymentContext(account, request, target, BigDecimal.ZERO));
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.saveClearance(any())).thenAnswer(call -> {
            FinancialClearance value = call.getArgument(0);
            return FinancialClearance.restore(UUID.randomUUID(), value.getAccountId(), value.getPaymentRequestId(), value.getInvoiceId(),
                    value.getPatientId(), value.getPurpose(), value.getCareEpisodeType(), value.getCareEpisodeId(), value.getAppointmentId(),
                    value.getRecordId(), value.getLabTestIds(), value.getPrescriptionId(), value.getAdmissionId(), value.getSurgeryCaseId(),
                    value.getAmount(), value.getCurrency(), value.getPaymentMethod(), false, value.getExpiresAt(), value.getGrantedAt(), null);
        });
    }

    @Test void partialPaymentProducesReceiptButNoClearance() {
        var payment = service.complete(requestId, command("part", "30"), actor, "trace");
        assertThat(payment.amount()).isEqualByComparingTo("30");
        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.PARTIALLY_PAID);
        verify(repository, never()).saveClearance(any());
        var event = ArgumentCaptor.forClass(LedgerIntegrationEvent.class);
        verify(events).appendHeld(eq(account.getAccountId()), event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("payment.completed");
        assertThat(((LedgerIntegrationEvent.PaymentCompletedPayload) event.getValue().payload()).classification())
                .isEqualTo("SERVICE_PAYMENT");
    }

    @Test void thirdInstallmentFinishesRequestWithExactlyOnePurposeClearance() {
        request.markPartiallyPaid();
        when(repository.lockRequest(requestId)).thenReturn(new PaymentContext(account, request, target, new BigDecimal("80")));
        service.complete(requestId, command("final", "20"), actor, "trace");
        assertThat(request.isSettled()).isTrue();
        var clearance = ArgumentCaptor.forClass(FinancialClearance.class);
        verify(repository).saveClearance(clearance.capture());
        assertThat(clearance.getValue().getPrescriptionId()).isEqualTo(target.prescriptionId());
        assertThat(clearance.getValue().getAmount()).isEqualByComparingTo("100");
        verify(events, times(2)).appendHeld(any(), any());
    }

    @Test void anotherPartialInstallmentDoesNotRejectValidTransition() {
        request.markPartiallyPaid();
        when(repository.lockRequest(requestId)).thenReturn(new PaymentContext(account, request, target, new BigDecimal("20")));
        service.complete(requestId, command("part2", "30"), actor, "trace");
        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.PARTIALLY_PAID);
    }

    @Test void depositIsCashLiabilityWithoutAllocationToServiceCharges() {
        account = BillingAccount.restore(account.getAccountId(), account.getPatientId(), account.getDepartmentId(), CareEpisodeType.ADMISSION,
                episode, AccountStatus.OPEN, "VND", 0, now, null, null, now, now);
        request = PaymentRequest.restore(requestId, request.getInvoiceId(), account.getAccountId(), PaymentRequestPurpose.ADMISSION_DEPOSIT,
                PaymentRequestStatus.PENDING, new BigDecimal("100"), "VND", null, actor, now, null);
        target = new ClearanceTarget(null, null, List.of(), null, episode, null);
        var payment = service.complete(requestId, command("deposit", "100"), actor, "trace");
        assertThat(payment.classification()).isEqualTo(PaymentClassification.ADMISSION_DEPOSIT);
        verify(repository, never()).allocate(any());
    }

    @Test void expiredOrExcessPaymentDoesNotWriteAnything() {
        assertThatThrownBy(() -> service.complete(requestId, command("excess", "101"), actor, "trace"))
                .isInstanceOf(BillingRuleException.class).hasMessage("BILLING_PAYMENT_EXCEEDS_REQUEST");
        request = PaymentRequest.restore(requestId, request.getInvoiceId(), account.getAccountId(), request.getPurpose(), request.getStatus(),
                request.getRequestedAmount(), "VND", now, actor, now, null);
        assertThatThrownBy(() -> service.complete(requestId, command("expired", "10"), actor, "trace"))
                .hasMessage("BILLING_REQUEST_EXPIRED");
        verify(repository, never()).save(any(), any(), any());
        verifyNoInteractions(events);
    }

    @Test void idempotentReplayReturnsOriginalPaymentEvenAfterExpiry() {
        var transaction = PaymentTransaction.restore(UUID.randomUUID(), account.getAccountId(), requestId, PaymentTransactionType.PAYMENT,
                PaymentClassification.SERVICE_PAYMENT, PaymentTransactionStatus.COMPLETED, new BigDecimal("20"), "VND", "CASH",
                null, "once", null, now, now);
        when(repository.findByIdempotencyKey("once")).thenReturn(Optional.of(new RecordedPayment(transaction, actor)));
        assertThat(service.complete(requestId, command("once", "20.00"), actor, "new trace").transactionId())
                .isEqualTo(transaction.getTransactionId());
        verify(repository, never()).save(any(), any(), any());
        verifyNoInteractions(events);
        assertThatThrownBy(() -> service.complete(requestId, command("once", "21"), actor, "trace"))
                .hasMessage("BILLING_IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(() -> service.complete(requestId, command("once", "20"), UUID.randomUUID(), "trace"))
                .hasMessage("BILLING_IDEMPOTENCY_CONFLICT");
    }

    @Test void purposeCannotCarryUnrelatedTargetsOrForgeEmergency() {
        target = new ClearanceTarget(null, null, List.of(), target.prescriptionId(), UUID.randomUUID(), null);
        assertThatThrownBy(() -> service.complete(requestId, command("wrong", "100"), actor, "trace"))
                .hasMessageContaining("Target không khớp");
        verify(repository, never()).save(any(), any(), any());
    }

    @Test void insuranceApprovalIsNotACompletedCashReceipt() {
        var command = new CompleteLedgerPaymentRequest("insurance", new BigDecimal("100"), "VND", PaymentMethod.INSURANCE, null);
        assertThatThrownBy(() -> service.complete(requestId, command, actor, "trace"))
                .hasMessage("BILLING_INSURANCE_IS_NOT_CASH");
        verifyNoInteractions(repository, events);
    }

    private CompleteLedgerPaymentRequest command(String key, String amount) {
        return new CompleteLedgerPaymentRequest(key, new BigDecimal(amount), "VND", PaymentMethod.CASH, null);
    }
}
