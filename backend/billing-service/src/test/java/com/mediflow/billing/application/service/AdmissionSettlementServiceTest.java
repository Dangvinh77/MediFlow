package com.mediflow.billing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.billing.application.dto.request.SettleAdmissionRequest;
import com.mediflow.billing.application.event.DischargeMedicallyApprovedEvent;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.port.out.AdmissionSettlementRepositoryPort;
import com.mediflow.billing.application.port.out.AdmissionSettlementRepositoryPort.ChargeRemaining;
import com.mediflow.billing.application.port.out.AdmissionSettlementRepositoryPort.SettlementContext;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.domain.model.Settlement;
import com.mediflow.billing.domain.model.SettlementOutcome;

class AdmissionSettlementServiceTest {
    private final ProcessedEventPort processedEvent = mock(ProcessedEventPort.class);
    private final AdmissionSettlementRepositoryPort repository = mock(AdmissionSettlementRepositoryPort.class);
    private final LedgerEventPort events = mock(LedgerEventPort.class);
    private final Instant now = Instant.parse("2026-10-10T08:00:00Z");
    private final AdmissionSettlementService service =
            new AdmissionSettlementService(processedEvent, repository, events, Clock.fixed(now, ZoneOffset.UTC));

    private UUID accountId;
    private UUID admissionId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        admissionId = UUID.randomUUID();
    }

    private BillingAccount account(AccountStatus status) {
        return BillingAccount.restore(accountId, UUID.randomUUID(), UUID.randomUUID(), CareEpisodeType.ADMISSION,
                admissionId, status, "VND", 0, now, status != AccountStatus.OPEN ? now : null, null, now, now);
    }

    // --- discharge freeze ---

    @Test
    void onDischargeMedicallyApproved_closesChargesOnTheExactAdmissionAccount() {
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
        var account = account(AccountStatus.OPEN);
        when(repository.lockAccountByAdmission(admissionId)).thenReturn(account);
        UUID patientId = account.getPatientId();
        var event = new DischargeMedicallyApprovedEvent(UUID.randomUUID(), now, "corr",
                new DischargeMedicallyApprovedEvent.Payload(admissionId, patientId, UUID.randomUUID(), UUID.randomUUID(), now));

        service.onDischargeMedicallyApproved(event);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.CHARGE_CLOSED);
        verify(repository).updateAccountStatus(account);
        verify(processedEvent).markProcessed(event.eventId(), "discharge.medically.approved");
    }

    @Test
    void onDischargeMedicallyApproved_duplicateEvent_isNoOp() {
        when(processedEvent.alreadyProcessed(any())).thenReturn(true);
        var event = new DischargeMedicallyApprovedEvent(UUID.randomUUID(), now, "corr",
                new DischargeMedicallyApprovedEvent.Payload(admissionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now));

        service.onDischargeMedicallyApproved(event);

        verifyNoInteractions(repository);
    }

    // --- settle ---

    @Test
    void settle_notReady_rejectsWhenAccountStillOpen() {
        when(repository.lockAccountById(accountId)).thenReturn(account(AccountStatus.OPEN));

        assertThatThrownBy(() -> service.settle(accountId, new SettleAdmissionRequest(null, null, null), UUID.randomUUID(), "corr"))
                .isInstanceOf(BillingRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BILLING_SETTLEMENT_NOT_READY");
        verify(repository, never()).loadContext(any());
    }

    @Test
    void settle_positiveBalance_requiresPayment() {
        when(repository.lockAccountById(accountId)).thenReturn(account(AccountStatus.CHARGE_CLOSED));
        var remaining = List.of(new ChargeRemaining(UUID.randomUUID(), new BigDecimal("50000.00")));
        when(repository.loadContext(accountId)).thenReturn(new SettlementContext(new BigDecimal("500000.00"),
                new BigDecimal("450000.00"), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), remaining, 1, null));
        when(repository.saveSettlement(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));
        UUID settlementRequestId = UUID.randomUUID();
        when(repository.createSettlementPaymentRequest(eq(accountId), eq(new BigDecimal("50000.00")), eq(remaining), any()))
                .thenReturn(settlementRequestId);

        var result = service.settle(accountId, new SettleAdmissionRequest(null, null, null), UUID.randomUUID(), "corr");

        assertThat(result.outcome()).isEqualTo(SettlementOutcome.ADDITIONAL_PAYMENT_REQUIRED);
        assertThat(result.settlementPaymentRequestId()).isEqualTo(settlementRequestId);
        verify(repository).createSettlementPaymentRequest(eq(accountId), eq(new BigDecimal("50000.00")), eq(remaining), any());
        verifyNoInteractions(events);
    }

    @Test
    void settle_negativeBalance_recordsRefundDueWithoutPublishing() {
        when(repository.lockAccountById(accountId)).thenReturn(account(AccountStatus.CHARGE_CLOSED));
        when(repository.loadContext(accountId)).thenReturn(new SettlementContext(new BigDecimal("500000.00"),
                new BigDecimal("600000.00"), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), List.of(), 1, null));
        when(repository.saveSettlement(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

        var result = service.settle(accountId, new SettleAdmissionRequest(null, null, null), UUID.randomUUID(), "corr");

        assertThat(result.outcome()).isEqualTo(SettlementOutcome.REFUND_DUE);
        assertThat(result.settlementPaymentRequestId()).isNull();
        verify(repository, never()).createSettlementPaymentRequest(any(), any(), anyList(), any());
        verifyNoInteractions(events);
    }

    @Test
    void settle_zeroBalance_paidInFullPublishesSettlementCompleted() {
        var account = account(AccountStatus.CHARGE_CLOSED);
        when(repository.lockAccountById(accountId)).thenReturn(account);
        when(repository.loadContext(accountId)).thenReturn(new SettlementContext(new BigDecimal("500000.00"),
                new BigDecimal("500000.00"), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), List.of(), 1, null));
        when(repository.saveSettlement(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

        var result = service.settle(accountId, new SettleAdmissionRequest(null, null, null), UUID.randomUUID(), "corr");

        assertThat(result.outcome()).isEqualTo(SettlementOutcome.PAID_IN_FULL);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SETTLED);
        verify(events).appendHeld(eq(accountId), any(LedgerIntegrationEvent.class));
        verify(repository).updateAccountStatus(account);
    }

    @Test
    void settle_approvedDebt_overridesOutcomeDespitePositiveBalance() {
        var account = account(AccountStatus.CHARGE_CLOSED);
        when(repository.lockAccountById(accountId)).thenReturn(account);
        when(repository.loadContext(accountId)).thenReturn(new SettlementContext(new BigDecimal("500000.00"),
                new BigDecimal("0.00"), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), List.of(), 1, null));
        when(repository.saveSettlement(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

        var result = service.settle(accountId,
                new SettleAdmissionRequest(null, null, SettlementOutcome.DEBT_APPROVED), UUID.randomUUID(), "corr");

        assertThat(result.outcome()).isEqualTo(SettlementOutcome.DEBT_APPROVED);
        assertThat(result.balance()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SETTLED);
        verify(repository, never()).createSettlementPaymentRequest(any(), any(), anyList(), any());
        verify(events).appendHeld(eq(accountId), any(LedgerIntegrationEvent.class));
    }

    @Test
    void settle_positiveBalanceWithInsurance_rejectsUnsupportedPaymentRequest() {
        when(repository.lockAccountById(accountId)).thenReturn(account(AccountStatus.CHARGE_CLOSED));
        when(repository.loadContext(accountId)).thenReturn(new SettlementContext(new BigDecimal("500000.00"),
                new BigDecimal("0.00"), BigDecimal.ZERO.setScale(2), new BigDecimal("100000.00"), List.of(), 1, null));

        assertThatThrownBy(() -> service.settle(accountId,
                new SettleAdmissionRequest(new BigDecimal("100000.00"), "DECISION-1", null), UUID.randomUUID(), "corr"))
                .isInstanceOf(BillingRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BILLING_SETTLEMENT_INSURANCE_PAYMENT_REQUEST_UNSUPPORTED");
        verify(repository, never()).saveSettlement(any());
    }

    @Test
    void settle_insuranceWithoutDecisionReference_rejects() {
        when(repository.lockAccountById(accountId)).thenReturn(account(AccountStatus.CHARGE_CLOSED));

        assertThatThrownBy(() -> service.settle(accountId,
                new SettleAdmissionRequest(new BigDecimal("100000.00"), null, null), UUID.randomUUID(), "corr"))
                .isInstanceOf(BillingRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BILLING_SETTLEMENT_INSURANCE_CONTEXT_REQUIRED");
    }

    @Test
    void settle_outpatientAccount_rejectsEpisodeMismatch() {
        var account = BillingAccount.restore(accountId, UUID.randomUUID(), UUID.randomUUID(), CareEpisodeType.OUTPATIENT_VISIT,
                UUID.randomUUID(), AccountStatus.CHARGE_CLOSED, "VND", 0, now, now, null, now, now);
        when(repository.lockAccountById(accountId)).thenReturn(account);

        assertThatThrownBy(() -> service.settle(accountId, new SettleAdmissionRequest(null, null, null), UUID.randomUUID(), "corr"))
                .isInstanceOf(BillingRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BILLING_SETTLEMENT_EPISODE_MISMATCH");
    }

    @Test
    void settle_secondCallOnSettlementPending_doesNotReopenCharges() {
        var account = account(AccountStatus.SETTLEMENT_PENDING);
        when(repository.lockAccountById(accountId)).thenReturn(account);
        when(repository.loadContext(accountId)).thenReturn(new SettlementContext(new BigDecimal("500000.00"),
                new BigDecimal("500000.00"), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), List.of(), 2, UUID.randomUUID()));
        when(repository.saveSettlement(any())).thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

        var result = service.settle(accountId, new SettleAdmissionRequest(null, null, null), UUID.randomUUID(), "corr");

        assertThat(result.outcome()).isEqualTo(SettlementOutcome.PAID_IN_FULL);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SETTLED);
    }

    private static Settlement withId(Settlement s, UUID id) {
        return Settlement.restore(id, s.getAccountId(), s.getAdmissionId(), s.getSettlementVersion(),
                s.getSupersedesSettlementId(), s.getGrossAmount(), s.getInsuranceAmount(), s.getPatientLiability(),
                s.getCompletedPayments(), s.getCompletedRefunds(), s.getBalance(), s.getOutcome(), s.getCompletedAt());
    }
}
