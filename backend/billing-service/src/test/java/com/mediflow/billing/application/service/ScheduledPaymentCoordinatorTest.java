package com.mediflow.billing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.billing.application.dto.command.ScheduledPaymentCommand;
import com.mediflow.billing.application.dto.response.ScheduledPaymentResult;
import com.mediflow.billing.application.port.out.ClinicalPaymentLockPort;
import com.mediflow.billing.application.port.out.ScheduledPaymentTransactionsPort;
import com.mediflow.billing.application.port.out.ScheduledPaymentTransactionsPort.ClosePaymentCommand;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.domain.model.PaymentOperationStatus;
import com.mediflow.billing.domain.model.ScheduledPaymentOperation;
import com.mediflow.billing.domain.model.ScheduledPaymentLease;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduledPaymentCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");
    private static final Instant COMPLETION_TIME = Instant.parse("2026-10-26T08:00:03Z");

    private final ScheduledPaymentTransactionsPort transactions = mock(ScheduledPaymentTransactionsPort.class);
    private final ClinicalPaymentLockPort clinical = mock(ClinicalPaymentLockPort.class);
    private final List<String> order = new ArrayList<>();
    private ScheduledPaymentCoordinator coordinator;
    private UUID requestId;
    private UUID actorId;
    private UUID accountId;
    private UUID patientId;
    private UUID operationId;
    private UUID registrationId;
    private UUID reservationId;
    private ScheduledPaymentCommand command;
    private ScheduledPaymentOperation preparing;
    private ScheduledPaymentLease lease;
    private ScheduledPaymentResult completed;

    @BeforeEach
    void setUp() {
        coordinator = new ScheduledPaymentCoordinator(transactions, clinical,
                Clock.fixed(COMPLETION_TIME, ZoneOffset.UTC));
        requestId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        operationId = UUID.randomUUID();
        registrationId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        command = new ScheduledPaymentCommand(reservationId, 4, new BigDecimal("100000.00"), "VND",
                PaymentMethod.CASH, null);
        preparing = new ScheduledPaymentOperation(operationId, requestId, accountId, registrationId, reservationId,
                patientId, "EXAM", registrationId, registrationId, actorId, "counter-4", "fingerprint",
                "scheduled-trace", 4, command.amount(), command.currency(), command.paymentMethod(),
                command.providerReference(), PaymentOperationStatus.PREPARING, null, null, null, null, null, null,
                NOW, NOW, null, null);
        lease = new ScheduledPaymentLease(operationId, reservationId, 5, 12,
                NOW.plusSeconds(1), NOW.plusSeconds(121));
        completed = ScheduledPaymentResult.completed(operationId, requestId, registrationId, reservationId,
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100000.00"), "VND", NOW, NOW.plusSeconds(2));
    }

    @Test
    void persistsPreparingBeforeCallingClinicalAndCommitsOnlyAfterLeaseIsPersisted() {
        when(transactions.prepare(eq(requestId), eq(command), eq(actorId), eq("counter-4"), any(), eq("scheduled-trace"), eq(NOW)))
                .thenAnswer(invocation -> {
                    order.add("prepare");
                    return preparing;
                });
        when(clinical.acquire(preparing)).thenAnswer(invocation -> {
            assertThat(order).containsExactly("prepare");
            order.add("acquire");
            return lease;
        });
        when(transactions.recordLease(operationId, lease, NOW)).thenAnswer(invocation -> {
            order.add("lease");
            return preparing.withLease(lease, NOW);
        });
        var completedOperation = preparing.withLease(lease, NOW.plusSeconds(1)).completed(
                completed.operation().transactionId(), completed.clearanceId(), NOW.plusSeconds(2), NOW.plusSeconds(2));
        when(transactions.complete(operationId, "scheduled-trace", COMPLETION_TIME)).thenAnswer(invocation -> {
            order.add("commit");
            return completedOperation;
        });
        doAnswer(invocation -> {
            order.add("resolve");
            return null;
        }).when(clinical).resolve(any());
        when(transactions.currentResult(operationId)).thenReturn(completed);

        ScheduledPaymentResult result = coordinator.pay(requestId, command, actorId, "counter-4",
                "scheduled-trace", NOW);

        assertThat(result.operation().status()).isEqualTo(PaymentOperationStatus.COMPLETED);
        assertThat(order).containsExactly("prepare", "acquire", "lease", "commit", "resolve");
        verify(transactions).markResolutionApplied(operationId, NOW);
    }

    @Test
    void acquireTimeoutKeepsTheSameOperationPreparingAndDoesNotWriteCash() {
        when(transactions.prepare(eq(requestId), eq(command), eq(actorId), eq("counter-4"), any(), eq("scheduled-trace"), eq(NOW)))
                .thenReturn(preparing);
        when(clinical.acquire(preparing)).thenThrow(new ClinicalPaymentLockPort.PaymentAuthorityUnavailableException("timeout"));
        when(transactions.currentResult(operationId)).thenReturn(ScheduledPaymentResult.preparing(preparing));

        ScheduledPaymentResult result = coordinator.pay(requestId, command, actorId, "counter-4",
                "scheduled-trace", NOW);

        assertThat(result.operation().paymentOperationId()).isEqualTo(operationId);
        assertThat(result.operation().status()).isEqualTo(PaymentOperationStatus.PREPARING);
        verify(transactions, never()).complete(eq(operationId), any(), any());
        verify(transactions, never()).closeAcquireRejected(eq(operationId), any(), any());
    }

    @Test
    void definitiveClinicalBindingConflictClosesWithoutWritingCash() {
        var closed = ScheduledPaymentResult.closed(operationId, requestId, registrationId, reservationId,
                new BigDecimal("100000.00"), "VND", NOW, NOW.plusSeconds(1));
        when(transactions.prepare(eq(requestId), eq(command), eq(actorId), eq("counter-4"), any(), eq("scheduled-trace"), eq(NOW)))
                .thenReturn(preparing);
        when(clinical.acquire(preparing)).thenThrow(new ClinicalPaymentLockPort.PaymentAcquireRejectedException(
                "IDEMPOTENCY_CONFLICT", "operation binding conflicts at Clinical"));
        when(transactions.closeAcquireRejected(operationId, "IDEMPOTENCY_CONFLICT", NOW)).thenReturn(closed);

        var result = coordinator.pay(requestId, command, actorId, "counter-4", "scheduled-trace", NOW);

        assertThat(result.operation().status()).isEqualTo(PaymentOperationStatus.CLOSED_NOT_PAID);
        verify(transactions).closeAcquireRejected(operationId, "IDEMPOTENCY_CONFLICT", NOW);
        verify(transactions, never()).complete(eq(operationId), any(), any());
    }

    @Test
    void resourceNotFoundDoesNotProveNonpaymentOrCloseTheOperation() {
        when(transactions.prepare(eq(requestId), eq(command), eq(actorId), eq("counter-4"), any(), eq("scheduled-trace"), eq(NOW)))
                .thenReturn(preparing);
        when(clinical.acquire(preparing)).thenThrow(new ClinicalPaymentLockPort.PaymentAcquireRejectedException(
                "RESOURCE_NOT_FOUND", "Clinical cannot currently find this resource"));
        var current = ScheduledPaymentResult.preparing(preparing);
        when(transactions.currentResult(operationId)).thenReturn(current);

        var result = coordinator.pay(requestId, command, actorId, "counter-4", "scheduled-trace", NOW);

        assertThat(result.operation().status()).isEqualTo(PaymentOperationStatus.PREPARING);
        verify(transactions, never()).closeAcquireRejected(eq(operationId), any(), any());
        verify(transactions, never()).complete(eq(operationId), any(), any());
    }

    @Test
    void mismatchedBasisAndQuoteLeaseIsRejectedBeforePersistingLeaseOrCommitting() {
        UUID quoteId = UUID.randomUUID();
        ScheduledPaymentCommand zeroCommand = ScheduledPaymentCommand.zero(reservationId, 4, quoteId);
        ScheduledPaymentOperation zeroPreparing = new ScheduledPaymentOperation(operationId, requestId, accountId,
                registrationId, reservationId, patientId, "EXAM", registrationId, registrationId, actorId,
                "counter-4", "fingerprint", "scheduled-trace", 4, BigDecimal.ZERO, "VND", PaymentMethod.CASH,
                null, PaymentOperationStatus.PREPARING, null, null, null, null, null, null, NOW, NOW, null, null,
                quoteId, "ZERO_LIABILITY");
        ScheduledPaymentLease wrongBasisLease = new ScheduledPaymentLease(operationId, reservationId, 5, 12,
                NOW.plusSeconds(1), NOW.plusSeconds(121));
        when(transactions.prepare(eq(requestId), eq(zeroCommand), eq(actorId), eq("counter-4"), any(),
                eq("scheduled-trace"), eq(NOW))).thenReturn(zeroPreparing);
        when(clinical.acquire(zeroPreparing)).thenReturn(wrongBasisLease);
        when(transactions.recordLease(operationId, wrongBasisLease, NOW))
                .thenReturn(zeroPreparing.withLease(wrongBasisLease, NOW));
        when(transactions.complete(operationId, "scheduled-trace", COMPLETION_TIME))
                .thenReturn(zeroPreparing.withLease(wrongBasisLease, NOW).completed(null, UUID.randomUUID(),
                        COMPLETION_TIME, COMPLETION_TIME));
        when(transactions.currentResult(operationId)).thenReturn(ScheduledPaymentResult.preparing(zeroPreparing));

        var result = coordinator.pay(requestId, zeroCommand, actorId, "counter-4", "scheduled-trace", NOW);

        assertThat(result.operation().status()).isEqualTo(PaymentOperationStatus.PREPARING);
        verify(transactions, never()).recordLease(eq(operationId), any(), any());
        verify(transactions, never()).complete(eq(operationId), any(), any());
    }

    @Test
    void commitUsesClockInstantAfterClinicalLeaseAcquisition() {
        var fixedClockCoordinator = new ScheduledPaymentCoordinator(transactions, clinical,
                Clock.fixed(COMPLETION_TIME, ZoneOffset.UTC));
        when(transactions.prepare(eq(requestId), eq(command), eq(actorId), eq("counter-4"), any(),
                eq("scheduled-trace"), eq(NOW))).thenReturn(preparing);
        when(clinical.acquire(preparing)).thenReturn(lease);
        when(transactions.recordLease(operationId, lease, NOW)).thenReturn(preparing.withLease(lease, NOW));
        var committed = preparing.withLease(lease, NOW).completed(UUID.randomUUID(), UUID.randomUUID(),
                COMPLETION_TIME, COMPLETION_TIME);
        when(transactions.complete(operationId, "scheduled-trace", COMPLETION_TIME)).thenReturn(committed);
        when(transactions.currentResult(operationId)).thenReturn(completed);
        org.mockito.Mockito.doNothing().when(clinical).resolve(any());

        fixedClockCoordinator.pay(requestId, command, actorId, "counter-4", "scheduled-trace", NOW);

        verify(transactions).complete(operationId, "scheduled-trace", COMPLETION_TIME);
    }

    @Test
    void closeIfUnpaidUsesThePersistedOperationBindingAndFence() {
        var request = new ClosePaymentCommand(registrationId, reservationId, 12,
                "RESERVATION_RECONCILIATION");
        var closed = ScheduledPaymentResult.closed(operationId, requestId, registrationId, reservationId,
                new BigDecimal("100000.00"), "VND", NOW, NOW.plusSeconds(3));
        when(transactions.closeIfUnpaid(operationId, request, NOW)).thenReturn(closed);

        assertThat(coordinator.closeIfUnpaid(operationId, request, NOW).operation().status())
                .isEqualTo(PaymentOperationStatus.CLOSED_NOT_PAID);
        verify(transactions).closeIfUnpaid(operationId, request, NOW);
    }

    @Test
    void closedOperationCannotBeReopenedThroughPaymentReplay() {
        when(transactions.prepare(eq(requestId), eq(command), eq(actorId), eq("counter-4"), any(), eq("trace"), eq(NOW)))
                .thenThrow(new BillingRuleException("BILLING_PAYMENT_OPERATION_CLOSED", "closed"));

        assertThatThrownBy(() -> coordinator.pay(requestId, command, actorId, "counter-4", "trace", NOW))
                .hasMessage("closed");
        verify(clinical, never()).acquire(any());
    }
}
