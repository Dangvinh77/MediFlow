package com.mediflow.billing.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.out.*;
import com.mediflow.billing.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

class LedgerRefundServiceTest {
    private final LedgerPaymentRepositoryPort payments = mock(LedgerPaymentRepositoryPort.class);
    private final LedgerRefundRepositoryPort refunds = mock(LedgerRefundRepositoryPort.class);
    private final LedgerEventPort events = mock(LedgerEventPort.class);
    private final Instant now = Instant.parse("2026-10-08T04:00:00Z");
    private final UUID accountId = UUID.randomUUID(), originalId = UUID.randomUUID(), actor = UUID.randomUUID();
    private final LedgerRefundService service = new LedgerRefundService(payments, refunds, events,
            Mappers.getMapper(LedgerPaymentMapper.class), Clock.fixed(now, ZoneOffset.UTC));
    private BillingAccount account;
    private PaymentTransaction original;

    @BeforeEach void setup() {
        account = BillingAccount.restore(accountId, UUID.randomUUID(), UUID.randomUUID(), CareEpisodeType.OUTPATIENT_VISIT,
                UUID.randomUUID(), AccountStatus.OPEN, "VND", 0, now.minusSeconds(100), null, null, now, now);
        original = transaction(originalId, PaymentTransactionType.PAYMENT, PaymentClassification.SERVICE_PAYMENT, null, "100", "paid");
        when(refunds.lockOriginal(originalId)).thenReturn(new LedgerRefundRepositoryPort.RefundContext(account, original, BigDecimal.ZERO));
        when(payments.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
    }

    @Test void refund_valid_appendsLinkedTransactionReversalsAndRedactedHeldEvent() {
        var result = service.refund(originalId, command("20"), actor, "trace");
        assertThat(result.originalTransactionId()).isEqualTo(originalId);
        assertThat(result.refundTransactionId()).isNotEqualTo(originalId);
        assertThat(result.amount()).isEqualByComparingTo("20");
        assertThat(original.getAmount()).isEqualByComparingTo("100");
        var captured = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(refunds).append(captured.capture(), eq("Private audit reason"), eq(actor));
        var refund = captured.getValue();
        assertThat(refund.isCompleted()).isTrue();
        assertThat(refund.getTransactionType()).isEqualTo(PaymentTransactionType.REFUND);
        verify(refunds).reverseAllocations(original, refund);
        verify(refunds).revokeUnsatisfiedClearance(original.getPaymentRequestId(), now);
        var event = ArgumentCaptor.forClass(LedgerIntegrationEvent.class);
        verify(events).appendHeld(eq(accountId), event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("payment.refunded");
        assertThat(((LedgerIntegrationEvent.PaymentRefundedPayload) event.getValue().payload()).reason())
                .isEqualTo(LedgerRefundService.PUBLIC_REASON);
        var order = inOrder(payments, refunds);
        order.verify(payments).lockIdempotencyKey("refund");
        order.verify(refunds).lockOriginal(originalId);
    }

    @Test void refund_exceedsRemainingIncludingPriorReversals_rejectsWithoutMutation() {
        when(refunds.lockOriginal(originalId)).thenReturn(new LedgerRefundRepositoryPort.RefundContext(account, original, new BigDecimal("90")));
        assertThatThrownBy(() -> service.refund(originalId, command("20"), actor, "trace")).hasMessage("BILLING_REFUND_EXCEEDS_PAYMENT");
        verify(refunds, never()).append(any(), any(), any()); verifyNoInteractions(events);
    }

    @Test void refund_exactReplayAfterAccountClose_returnsOriginalWithoutEffects() {
        account = BillingAccount.restore(accountId, account.getPatientId(), account.getDepartmentId(), account.getCareEpisodeType(),
                account.getCareEpisodeId(), AccountStatus.CLOSED, "VND", 1, now.minusSeconds(100), now, now, now, now);
        when(refunds.lockOriginal(originalId)).thenReturn(new LedgerRefundRepositoryPort.RefundContext(account, original, new BigDecimal("20")));
        var recorded = transaction(UUID.randomUUID(), PaymentTransactionType.REFUND, PaymentClassification.SERVICE_PAYMENT, originalId, "20", "refund");
        when(payments.findByIdempotencyKey("refund")).thenReturn(Optional.of(new LedgerPaymentRepositoryPort.RecordedPayment(recorded, actor)));
        when(refunds.refundReason(recorded.getTransactionId())).thenReturn(Optional.of("Private audit reason"));
        assertThat(service.refund(originalId, command("20.00"), actor, "retry").refundTransactionId()).isEqualTo(recorded.getTransactionId());
        verify(refunds, never()).append(any(), any(), any()); verifyNoInteractions(events);
    }

    @ParameterizedTest @ValueSource(strings = {"actor", "amount", "reason", "method", "original", "payment-key"})
    void refund_changedIdempotentIntent_conflicts(String changed) {
        var recorded = transaction(UUID.randomUUID(), changed.equals("payment-key") ? PaymentTransactionType.PAYMENT : PaymentTransactionType.REFUND,
                PaymentClassification.SERVICE_PAYMENT, changed.equals("original") ? UUID.randomUUID() : originalId, "20", "refund");
        when(payments.findByIdempotencyKey("refund")).thenReturn(Optional.of(new LedgerPaymentRepositoryPort.RecordedPayment(recorded,
                changed.equals("actor") ? UUID.randomUUID() : actor)));
        when(refunds.refundReason(recorded.getTransactionId())).thenReturn(Optional.of(changed.equals("reason") ? "Other" : "Private audit reason"));
        var command = new RefundLedgerPaymentRequest("refund", new BigDecimal(changed.equals("amount") ? "21" : "20"),
                "Private audit reason", changed.equals("method") ? PaymentMethod.TRANSFER : PaymentMethod.CASH);
        assertThatThrownBy(() -> service.refund(originalId, command, actor, "retry")).hasMessage("BILLING_IDEMPOTENCY_CONFLICT");
        verifyNoInteractions(events);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "0.001", "100000000000000000"})
    void refund_invalidMoney_rejectsBeforeLocks(String amount) {
        assertThatThrownBy(() -> service.refund(originalId, command(amount), actor, "trace")).hasMessage("BILLING_TRANSACTION_INVALID_AMOUNT");
        verifyNoInteractions(payments, refunds, events);
    }

    @Test void refund_insuranceMethod_isNotRecordedAsCashRefund() {
        assertThatThrownBy(() -> service.refund(originalId, new RefundLedgerPaymentRequest("refund", BigDecimal.ONE, "reason", PaymentMethod.INSURANCE),
                actor, "trace")).hasMessage("BILLING_INVALID_REFUND_METHOD");
        verifyNoInteractions(payments, refunds, events);
    }

    private RefundLedgerPaymentRequest command(String amount) {
        return new RefundLedgerPaymentRequest("refund", new BigDecimal(amount), "Private audit reason", PaymentMethod.CASH);
    }
    private PaymentTransaction transaction(UUID id, PaymentTransactionType type, PaymentClassification classification, UUID original, String amount, String key) {
        return PaymentTransaction.restore(id, accountId, type == PaymentTransactionType.PAYMENT ? UUID.randomUUID() : this.original.getPaymentRequestId(), type, classification, PaymentTransactionStatus.COMPLETED,
                new BigDecimal(amount), "VND", "CASH", null, key, original, now.minusSeconds(60), now.minusSeconds(60));
    }
}
