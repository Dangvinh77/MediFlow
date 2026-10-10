package com.mediflow.billing.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.billing.application.dto.request.SettleAdmissionRequest;
import com.mediflow.billing.application.dto.response.SettlementDTO;
import com.mediflow.billing.application.event.DischargeMedicallyApprovedEvent;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.port.in.ReactToDischargeApprovalUseCase;
import com.mediflow.billing.application.port.in.SettleAdmissionUseCase;
import com.mediflow.billing.application.port.out.AdmissionSettlementRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.domain.model.InsuranceAdjustment;
import com.mediflow.billing.domain.model.InsuranceAdjustmentType;
import com.mediflow.billing.domain.model.Settlement;
import com.mediflow.billing.domain.model.SettlementOutcome;

/**
 * HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT item 4 / backend-spec/care-finance-v2/06-billing.md §6
 * "Settle admission". Freezes charge intake after medical discharge, then computes and persists a
 * new immutable settlement version from the equations in §4. Re-callable: a non-terminal outcome
 * (REFUND_DUE, or ADDITIONAL_PAYMENT_REQUIRED awaiting its settlement payment) leaves the account in
 * SETTLEMENT_PENDING for a later re-settle once the cashier acts; only a terminal outcome publishes
 * {@code settlement.completed} and closes the account to SETTLED.
 *
 * <p>Deposit recognition (unapplied deposit cash becoming earned revenue at settlement) and insurance
 * adjustments on an outstanding positive balance are explicitly open per CONTRACT-CARE-BILLING-01
 * ("final settlement/recognition" remains open) — not guessed here.
 */
public final class AdmissionSettlementService implements ReactToDischargeApprovalUseCase, SettleAdmissionUseCase {
    private final ProcessedEventPort processedEvent;
    private final AdmissionSettlementRepositoryPort repository;
    private final LedgerEventPort events;
    private final Clock clock;

    public AdmissionSettlementService(ProcessedEventPort processedEvent, AdmissionSettlementRepositoryPort repository,
            LedgerEventPort events, Clock clock) {
        this.processedEvent = processedEvent;
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public void onDischargeMedicallyApproved(DischargeMedicallyApprovedEvent event) {
        if (processedEvent.alreadyProcessed(event.eventId())) {
            return;
        }
        BillingAccount account = repository.lockAccountByAdmission(event.admissionId());
        require(account.getPatientId().equals(event.patientId()), "BILLING_SETTLEMENT_EPISODE_MISMATCH");
        account.closeCharges(event.approvedAt());
        repository.updateAccountStatus(account);
        processedEvent.markProcessed(event.eventId(), "discharge.medically.approved");
    }

    @Override
    public SettlementDTO settle(UUID accountId, SettleAdmissionRequest request, UUID actorAccountId, String correlationId) {
        validate(request);
        BillingAccount account = repository.lockAccountById(accountId);
        require(account.getCareEpisodeType() == CareEpisodeType.ADMISSION, "BILLING_SETTLEMENT_EPISODE_MISMATCH");
        require(account.getStatus() == AccountStatus.CHARGE_CLOSED || account.getStatus() == AccountStatus.SETTLEMENT_PENDING,
                "BILLING_SETTLEMENT_NOT_READY");
        boolean firstCall = account.getStatus() == AccountStatus.CHARGE_CLOSED;
        Instant now = clock.instant();

        if (request.approvedInsuranceAmount() != null) {
            InsuranceAdjustment adjustment = InsuranceAdjustment.record(accountId, account.getCareEpisodeId(),
                    request.insuranceDecisionReference(), InsuranceAdjustmentType.APPROVAL, null,
                    request.approvedInsuranceAmount(), null, now);
            repository.recordInsuranceAdjustment(adjustment);
        }

        var context = repository.loadContext(accountId);
        BigDecimal patientLiability = context.grossAmount().subtract(context.cumulativeInsurance());
        BigDecimal allocatedPayments = context.completedPayments().subtract(context.completedRefunds());
        BigDecimal balance = patientLiability.subtract(allocatedPayments);

        SettlementOutcome outcome;
        if (request.approvedExceptionalOutcome() != null) {
            outcome = request.approvedExceptionalOutcome();
        } else if (balance.signum() == 0) {
            outcome = SettlementOutcome.PAID_IN_FULL;
        } else if (balance.signum() > 0) {
            outcome = SettlementOutcome.ADDITIONAL_PAYMENT_REQUIRED;
        } else {
            outcome = SettlementOutcome.REFUND_DUE;
        }
        if (outcome == SettlementOutcome.ADDITIONAL_PAYMENT_REQUIRED && context.cumulativeInsurance().signum() > 0) {
            throw new BillingRuleException("BILLING_SETTLEMENT_INSURANCE_PAYMENT_REQUEST_UNSUPPORTED",
                    "Settlement payment request issuance under an approved insurance adjustment is not yet implemented");
        }

        Settlement settlement = Settlement.create(accountId, account.getCareEpisodeId(), context.nextVersion(),
                context.previousSettlementId(), context.grossAmount(), context.cumulativeInsurance(), patientLiability,
                context.completedPayments(), context.completedRefunds(), balance, outcome, now);
        Settlement saved = repository.saveSettlement(settlement);

        UUID settlementPaymentRequestId = null;
        if (outcome == SettlementOutcome.ADDITIONAL_PAYMENT_REQUIRED) {
            settlementPaymentRequestId = repository.createSettlementPaymentRequest(accountId, balance,
                    context.remainingCharges(), actorAccountId);
        }

        if (firstCall) {
            account.beginSettlement();
        }
        boolean terminal = outcome == SettlementOutcome.PAID_IN_FULL || outcome == SettlementOutcome.DEBT_APPROVED
                || outcome == SettlementOutcome.WAIVED;
        if (terminal) {
            account.settle();
        }
        repository.updateAccountStatus(account);

        if (terminal) {
            events.appendHeld(accountId, new LedgerIntegrationEvent(UUID.randomUUID(), "settlement.completed", 1, now,
                    correlationId, "billing-service", new LedgerIntegrationEvent.SettlementCompletedPayload(
                        saved.getSettlementId(), saved.getAdmissionId(), accountId, account.getPatientId(),
                        account.getDepartmentId(), saved.getGrossAmount(), saved.getInsuranceAmount(),
                        saved.getPatientLiability(), saved.getCompletedPayments(), saved.getCompletedRefunds(),
                        saved.getBalance(), saved.getOutcome().name(), saved.getCompletedAt())));
        }

        return new SettlementDTO(saved.getSettlementId(), accountId, saved.getAdmissionId(), saved.getSettlementVersion(),
                saved.getGrossAmount(), saved.getInsuranceAmount(), saved.getPatientLiability(),
                saved.getCompletedPayments(), saved.getCompletedRefunds(), saved.getBalance(), saved.getOutcome(),
                saved.getCompletedAt(), settlementPaymentRequestId);
    }

    private static void validate(SettleAdmissionRequest request) {
        boolean insurancePaired = (request.approvedInsuranceAmount() == null) == (request.insuranceDecisionReference() == null
                || request.insuranceDecisionReference().isBlank());
        require(insurancePaired, "BILLING_SETTLEMENT_INSURANCE_CONTEXT_REQUIRED");
        require(request.approvedExceptionalOutcome() == null
                || request.approvedExceptionalOutcome() == SettlementOutcome.DEBT_APPROVED
                || request.approvedExceptionalOutcome() == SettlementOutcome.WAIVED,
                "BILLING_SETTLEMENT_OUTCOME_NOT_APPROVABLE");
    }

    private static void require(boolean valid, String code) {
        if (!valid) {
            throw new BillingRuleException(code, code);
        }
    }
}
