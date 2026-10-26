package com.mediflow.billing.application.service;

import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.port.in.GrantLabZeroPayableEligibilityUseCase;
import com.mediflow.billing.application.port.out.LabZeroPayableEligibilityRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.domain.model.ClearancePurpose;
import com.mediflow.billing.domain.model.FinancialClearance;
import com.mediflow.billing.domain.model.LabZeroPayableEligibilityReceipt;
import com.mediflow.billing.domain.model.OutpatientQuote;
import com.mediflow.billing.domain.model.PaymentRequestPurpose;
import com.mediflow.billing.domain.model.PaymentRequestStatus;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Grants a Lab zero-liability clearance with one local transaction and immutable replay evidence. */
public final class GrantLabZeroPayableEligibilityService implements GrantLabZeroPayableEligibilityUseCase {
    private final LedgerPaymentRepositoryPort ledger;
    private final LabZeroPayableEligibilityRepositoryPort receipts;
    private final OutpatientQuoteRepositoryPort quotes;
    private final LedgerEventPort events;
    private final Clock clock;

    public GrantLabZeroPayableEligibilityService(LedgerPaymentRepositoryPort ledger,
            LabZeroPayableEligibilityRepositoryPort receipts, OutpatientQuoteRepositoryPort quotes,
            LedgerEventPort events, Clock clock) {
        this.ledger = ledger;
        this.receipts = receipts;
        this.quotes = quotes;
        this.events = events;
        this.clock = clock;
    }

    @Override public LabZeroPayableEligibilityReceipt grant(UUID paymentRequestId, UUID quoteId,
            UUID actorAccountId, String idempotencyKey, String correlationId) {
        validateInput(paymentRequestId, quoteId, actorAccountId, idempotencyKey, correlationId);
        receipts.lockIdempotencyKey(idempotencyKey);
        var byKey = receipts.findByIdempotencyKey(idempotencyKey);
        if (byKey.isPresent()) {
            var original = byKey.get();
            require(original.paymentRequestId().equals(paymentRequestId) && original.quoteId().equals(quoteId)
                    && original.actorAccountId().equals(actorAccountId), "BILLING_IDEMPOTENCY_CONFLICT");
            return original;
        }

        receipts.lockLabSource(paymentRequestId);
        var context = ledger.lockRequest(paymentRequestId);
        if (receipts.findByPaymentRequestId(paymentRequestId).isPresent()) {
            throw new BillingRuleException("BILLING_ZERO_ELIGIBILITY_EXISTS", "Lab eligibility already exists for this request");
        }
        var account = context.account();
        var request = context.request();
        require(request.getPurpose() == PaymentRequestPurpose.LAB_TEST
                && request.getStatus() == PaymentRequestStatus.PENDING
                && request.getRequestedAmount().signum() == 0
                && context.completedAmount().signum() == 0, "BILLING_REQUEST_NOT_ZERO_PAYABLE");
        require(account.getStatus() == AccountStatus.OPEN && account.getCareEpisodeType() == CareEpisodeType.OUTPATIENT_VISIT,
                "BILLING_LAB_TEST_SOURCE_CONFLICT");
        require(request.getExpiresAt() != null && clock.instant().isBefore(request.getExpiresAt()), "BILLING_REQUEST_EXPIRED");
        require(context.target() != null, "BILLING_CLEARANCE_TARGET_MISSING");
        ledger.verifyRequestTargets(context);

        OutpatientQuote quote = quotes.findCurrent(paymentRequestId)
                .orElseThrow(() -> new BillingRuleException("BILLING_QUOTE_NOT_FOUND", "Current quote was not found"));
        var now = clock.instant();
        require(quote.quoteId().equals(quoteId) && quote.patientId().equals(account.getPatientId())
                && quote.careEpisodeType().equals(account.getCareEpisodeType().name())
                && quote.careEpisodeId().equals(account.getCareEpisodeId()) && quote.isPayableAt(now)
                && quote.patientPayable().signum() == 0 && quote.lines().size() == 1
                && "LAB_TEST".equals(quote.lines().getFirst().sourceType()), "BILLING_ZERO_ELIGIBILITY_INVALID");
        receipts.verifyExactLabBinding(paymentRequestId, context, quote);

        var completedAt = now.truncatedTo(ChronoUnit.MICROS);
        request.markPaid(completedAt);
        ledger.saveZeroLiability(request, actorAccountId);
        var target = context.target();
        var clearance = FinancialClearance.grant(account.getAccountId(), request.getPaymentRequestId(), request.getInvoiceId(),
                account.getPatientId(), ClearancePurpose.LAB_TEST, account.getCareEpisodeType(), account.getCareEpisodeId(),
                target.appointmentId(), target.recordId(), target.labTestIds(), target.prescriptionId(), target.admissionId(),
                target.surgeryCaseId(), request.getRequestedAmount(), request.getCurrency(), "ZERO_LIABILITY", false,
                request.getExpiresAt(), completedAt);
        clearance = ledger.saveClearance(clearance);
        var payload = new LedgerIntegrationEvent.ClearanceGrantedPayload(clearance.getClearanceId(), request.getInvoiceId(),
                account.getAccountId(), account.getPatientId(), account.getCareEpisodeType().name(), account.getCareEpisodeId(),
                ClearancePurpose.LAB_TEST.name(), target.appointmentId(), target.recordId(), target.labTestIds(),
                target.prescriptionId(), target.admissionId(), target.surgeryCaseId(), request.getRequestedAmount(),
                request.getCurrency(), "ZERO_LIABILITY", request.getExpiresAt(), false);
        events.appendForOutpatientRequest(account.getAccountId(), paymentRequestId, new LedgerIntegrationEvent(
                UUID.randomUUID(), "financial.clearance.granted", 1, completedAt, correlationId, "billing-service", payload));

        var receipt = new LabZeroPayableEligibilityReceipt(UUID.randomUUID(), paymentRequestId, quoteId,
                idempotencyKey, actorAccountId, correlationId, clearance.getClearanceId(), completedAt);
        receipts.append(receipt);
        return receipt;
    }

    private static void validateInput(UUID requestId, UUID quoteId, UUID actor, String key, String correlation) {
        require(requestId != null && quoteId != null && actor != null, "BILLING_PAYMENT_CONTEXT_REQUIRED");
        require(key != null && key.matches("[A-Za-z0-9._:-]{1,120}"), "BILLING_IDEMPOTENCY_KEY_REQUIRED");
        require(correlation != null && !correlation.isBlank() && correlation.length() <= 120,
                "INVALID_CORRELATION_ID");
    }

    private static void require(boolean valid, String code) {
        if (!valid && "BILLING_IDEMPOTENCY_CONFLICT".equals(code))
            throw new com.mediflow.common.exception.DuplicateResourceException(code, code);
        if (!valid) throw new BillingRuleException(code, code);
    }
}
