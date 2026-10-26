package com.mediflow.clinical.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.authority.PaymentRecoveryOperation;
import com.mediflow.clinical.application.dto.authority.ScheduledPaymentSnapshot;
import com.mediflow.clinical.application.dto.request.PaymentResolutionCommand;
import com.mediflow.clinical.application.port.out.OutpatientPaymentRecoveryPort;
import com.mediflow.clinical.application.port.out.ScheduledPaymentReconciliationPort;
import com.mediflow.clinical.domain.model.OutpatientPaymentOutcome;

class OutpatientPaymentRecoveryWorkerTest {
    private static final Instant NOW = Instant.parse("2026-10-26T04:00:00Z");
    private static final UUID OPERATION_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID REGISTRATION_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID CLEARANCE_ID = UUID.randomUUID();
    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final long FENCE = 12L;
    private static final String CORRELATION = "clinical-reconcile-corr";

    private final OutpatientPaymentRecoveryPort recovery = mock(OutpatientPaymentRecoveryPort.class);
    private final ScheduledPaymentReconciliationPort billing = mock(ScheduledPaymentReconciliationPort.class);
    private final OutpatientRegistrationApplicationService registrations =
            mock(OutpatientRegistrationApplicationService.class);
    private OutpatientPaymentRecoveryWorker worker;
    private PaymentRecoveryOperation operation;

    @BeforeEach
    void setUp() {
        worker = new OutpatientPaymentRecoveryWorker(recovery, billing, registrations,
                Clock.fixed(NOW, ZoneOffset.UTC));
        operation = new PaymentRecoveryOperation(OPERATION_ID, REQUEST_ID, REGISTRATION_ID, RESERVATION_ID,
                PATIENT_ID, "EXAM", REGISTRATION_ID, REGISTRATION_ID, FENCE, "PATIENT_PAYMENT", null,
                NOW.minusSeconds(120), NOW.minusSeconds(120), 1, OWNER);
        when(recovery.claimDue(any(), eq(NOW), any(), eq(10))).thenReturn(List.of(operation));
    }

    @Test
    void completedPayment_usesBillingCompletedAtAndAppliesExactReceipt() {
        when(billing.lookup(OPERATION_ID, REGISTRATION_ID, RESERVATION_ID, CORRELATION))
                .thenReturn(snapshot("PREPARING", "120.00", "VND", null, null, null, null, null));
        when(billing.closeIfUnpaid(OPERATION_ID, REGISTRATION_ID, RESERVATION_ID, FENCE,
                "RESERVATION_RECONCILIATION", CORRELATION))
                .thenReturn(snapshot("COMPLETED", "120.00", "VND", TRANSACTION_ID, CLEARANCE_ID,
                        NOW.minusSeconds(5), TRANSACTION_ID, NOW.minusSeconds(5)));

        worker.runOnce(CORRELATION);

        verify(registrations).resolvePayment(new PaymentResolutionCommand(OPERATION_ID, REQUEST_ID,
                REGISTRATION_ID, RESERVATION_ID, FENCE, OutpatientPaymentOutcome.COMPLETED,
                TRANSACTION_ID, CLEARANCE_ID, NOW.minusSeconds(5), "PATIENT_PAYMENT", null));
        verify(recovery).complete(operation);
    }

    @Test
    void zeroLiabilityCompletion_requiresQuoteAndClearanceAndKeepsNullTransaction() {
        operation = new PaymentRecoveryOperation(OPERATION_ID, REQUEST_ID, REGISTRATION_ID, RESERVATION_ID,
                PATIENT_ID, "EXAM", REGISTRATION_ID, REGISTRATION_ID, FENCE, "ZERO_LIABILITY", QUOTE_ID,
                NOW.minusSeconds(120), NOW.minusSeconds(120), 1, OWNER);
        when(recovery.claimDue(any(), eq(NOW), any(), eq(10))).thenReturn(List.of(operation));
        when(billing.lookup(OPERATION_ID, REGISTRATION_ID, RESERVATION_ID, CORRELATION))
                .thenReturn(snapshot("PREPARING", "0.00", "VND", null, null, null, null, null));
        when(billing.closeIfUnpaid(OPERATION_ID, REGISTRATION_ID, RESERVATION_ID, FENCE,
                "RESERVATION_RECONCILIATION", CORRELATION))
                .thenReturn(snapshot("COMPLETED", "0.00", "VND", null, CLEARANCE_ID,
                        NOW.minusSeconds(3), null, null));

        worker.runOnce(CORRELATION);

        verify(registrations).resolvePayment(new PaymentResolutionCommand(OPERATION_ID, REQUEST_ID,
                REGISTRATION_ID, RESERVATION_ID, FENCE, OutpatientPaymentOutcome.COMPLETED,
                null, CLEARANCE_ID, NOW.minusSeconds(3), "ZERO_LIABILITY", QUOTE_ID));
        verify(recovery).complete(operation);
    }

    @Test
    void closedOperation_replaysBasisAndQuoteIntoTombstoneResolution() {
        operation = new PaymentRecoveryOperation(OPERATION_ID, REQUEST_ID, REGISTRATION_ID, RESERVATION_ID,
                PATIENT_ID, "EXAM", REGISTRATION_ID, REGISTRATION_ID, FENCE, "ZERO_LIABILITY", QUOTE_ID,
                NOW.minusSeconds(120), NOW.minusSeconds(120), 1, OWNER);
        when(recovery.claimDue(any(), eq(NOW), any(), eq(10))).thenReturn(List.of(operation));
        when(billing.lookup(OPERATION_ID, REGISTRATION_ID, RESERVATION_ID, CORRELATION))
                .thenReturn(snapshot("PREPARING", "0.00", "VND", null, null, null, null, null));
        when(billing.closeIfUnpaid(OPERATION_ID, REGISTRATION_ID, RESERVATION_ID, FENCE,
                "RESERVATION_RECONCILIATION", CORRELATION))
                .thenReturn(snapshot("CLOSED_NOT_PAID", "0.00", "VND", null, null, null, null, null));

        worker.runOnce(CORRELATION);

        verify(registrations).resolvePayment(new PaymentResolutionCommand(OPERATION_ID, REQUEST_ID,
                REGISTRATION_ID, RESERVATION_ID, FENCE, OutpatientPaymentOutcome.CLOSED_NOT_PAID,
                null, null, null, "ZERO_LIABILITY", QUOTE_ID));
        verify(recovery).complete(operation);
    }

    @Test
    void missingBillingOperation_keepsLocalHoldAndSchedulesRetry() {
        when(billing.lookup(OPERATION_ID, REGISTRATION_ID, RESERVATION_ID, CORRELATION))
                .thenThrow(new IllegalStateException("Billing has no operation yet"));

        worker.runOnce(CORRELATION);

        verify(billing, never()).closeIfUnpaid(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(), any());
        verify(registrations, never()).resolvePayment(any());
        verify(recovery).defer(eq(operation), eq(NOW), any(), any());
    }

    @Test
    void positivePaymentWithDifferentCompletionTimestamp_isNotApplied() {
        when(billing.lookup(OPERATION_ID, REGISTRATION_ID, RESERVATION_ID, CORRELATION))
                .thenReturn(snapshot("PREPARING", "120.00", "VND", null, null, null, null, null));
        when(billing.closeIfUnpaid(OPERATION_ID, REGISTRATION_ID, RESERVATION_ID, FENCE,
                "RESERVATION_RECONCILIATION", CORRELATION))
                .thenReturn(snapshot("COMPLETED", "120.00", "VND", TRANSACTION_ID, CLEARANCE_ID,
                        NOW.minusSeconds(5), TRANSACTION_ID, NOW.minusSeconds(4)));

        worker.runOnce(CORRELATION);

        verify(registrations, never()).resolvePayment(any());
        verify(recovery).defer(eq(operation), eq(NOW), any(), any());
    }

    private static ScheduledPaymentSnapshot snapshot(String status, String amount, String currency,
            UUID transactionId, UUID clearanceId, Instant operationCompletedAt,
            UUID paymentTransactionId, Instant paymentCompletedAt) {
        boolean zeroLiability = new BigDecimal(amount).signum() == 0;
        return new ScheduledPaymentSnapshot(new ScheduledPaymentSnapshot.Operation(OPERATION_ID, REQUEST_ID,
                REGISTRATION_ID, RESERVATION_ID, status, transactionId, amount, currency,
                NOW.minusSeconds(600), NOW, zeroLiability ? "ZERO_LIABILITY" : "PATIENT_PAYMENT",
                zeroLiability ? QUOTE_ID : null, operationCompletedAt),
                paymentTransactionId == null ? null : new ScheduledPaymentSnapshot.Payment(paymentTransactionId,
                        "COMPLETED", new BigDecimal(amount).toPlainString(), currency, "CASH", paymentCompletedAt),
                clearanceId);
    }
}
