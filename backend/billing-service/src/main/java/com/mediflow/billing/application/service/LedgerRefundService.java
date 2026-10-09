package com.mediflow.billing.application.service;

import java.time.Clock;
import java.util.UUID;
import java.util.Objects;
import com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest;
import com.mediflow.billing.application.dto.response.LedgerRefundDTO;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.PaymentRefundedPayload;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.in.RefundLedgerPaymentUseCase;
import com.mediflow.billing.application.port.out.*;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.*;
import com.mediflow.common.exception.DuplicateResourceException;

/** V2 spec §6: bounded, append-only refunds and reversal allocations commit with a held fact. */
public class LedgerRefundService implements RefundLedgerPaymentUseCase {
    public static final String PUBLIC_REASON = "CASHIER_RECORDED_REFUND";
    private final LedgerPaymentRepositoryPort payments;
    private final LedgerRefundRepositoryPort refunds;
    private final LedgerEventPort events;
    private final LedgerPaymentMapper mapper;
    private final Clock clock;

    public LedgerRefundService(LedgerPaymentRepositoryPort payments, LedgerRefundRepositoryPort refunds,
            LedgerEventPort events, LedgerPaymentMapper mapper, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public LedgerRefundDTO refund(UUID originalId, RefundLedgerPaymentRequest command, UUID actor, String correlation) {
        validate(originalId, command, actor, correlation);
        // Always key → account → original. Payment uses the same key/account order.
        payments.lockIdempotencyKey(command.idempotencyKey());
        var context = refunds.lockOriginal(originalId);
        var previous = payments.findByIdempotencyKey(command.idempotencyKey());
        if (previous.isPresent()) {
            var recorded = previous.get();
            var transaction = recorded.transaction();
            if (transaction.getTransactionType() != PaymentTransactionType.REFUND || !transaction.isCompleted()
                    || !originalId.equals(transaction.getOriginalTransactionId())
                    || !context.original().isCompleted() || context.original().getTransactionType() != PaymentTransactionType.PAYMENT
                    || !context.original().getAccountId().equals(transaction.getAccountId())
                    || !Objects.equals(context.original().getPaymentRequestId(), transaction.getPaymentRequestId())
                    || context.original().getClassification() != transaction.getClassification()
                    || !context.original().getCurrency().equals(transaction.getCurrency())
                    || !actor.equals(recorded.actorAccountId())
                    || command.amount().compareTo(transaction.getAmount()) != 0
                    || !command.paymentMethod().name().equals(transaction.getPaymentMethod())
                    || !refunds.refundReason(transaction.getTransactionId()).filter(command.reason()::equals).isPresent()) {
                throw new DuplicateResourceException("BILLING_IDEMPOTENCY_CONFLICT", "BILLING_IDEMPOTENCY_CONFLICT");
            }
            return mapper.toRefundDTO(transaction);
        }
        var original = context.original();
        var account = context.account();
        require(original.isCompleted() && original.getTransactionType() == PaymentTransactionType.PAYMENT,
                "BILLING_REFUND_REQUIRES_COMPLETED_PAYMENT");
        require(account.getAccountId().equals(original.getAccountId())
                && account.getCurrency().equals(original.getCurrency()), "BILLING_REFUND_ACCOUNT_MISMATCH");
        require(account.getStatus() != AccountStatus.CLOSED && account.getStatus() != AccountStatus.SETTLED,
                "BILLING_REFUND_REQUIRES_ACCOUNT_REOPEN");
        require(context.completedRefunds().add(command.amount()).compareTo(original.getAmount()) <= 0,
                "BILLING_REFUND_EXCEEDS_PAYMENT");
        var now = clock.instant();
        require(original.getCompletedAt() != null && !now.isBefore(original.getCompletedAt()), "BILLING_REFUND_TIME_INVALID");
        var refund = PaymentTransaction.restore(UUID.randomUUID(), original.getAccountId(), original.getPaymentRequestId(),
                PaymentTransactionType.REFUND, original.getClassification(), PaymentTransactionStatus.PENDING,
                command.amount(), original.getCurrency(), command.paymentMethod().name(), null, command.idempotencyKey(),
                originalId, null, now);
        refund.complete(now);
        refunds.append(refund, command.reason(), actor);
        refunds.reverseAllocations(original, refund);
        // Irreversible denial; this operation never reopens a PAID request or fabricates another grant.
        refunds.revokeUnsatisfiedClearance(original.getPaymentRequestId(), now);
        events.appendHeld(account.getAccountId(), new LedgerIntegrationEvent(UUID.randomUUID(), "payment.refunded", 1,
                now, correlation, "billing-service", new PaymentRefundedPayload(refund.getTransactionId(), originalId,
                account.getAccountId(), account.getPatientId(), account.getDepartmentId(), account.getCareEpisodeType().name(),
                account.getCareEpisodeId(), refund.getAmount(), refund.getCurrency(), PUBLIC_REASON, now)));
        return mapper.toRefundDTO(refund);
    }

    private static void validate(UUID original, RefundLedgerPaymentRequest command, UUID actor, String correlation) {
        require(original != null && command != null && actor != null && correlation != null
                && !correlation.isBlank() && correlation.length() <= 120, "BILLING_REFUND_CONTEXT_REQUIRED");
        var amount = command.amount();
        require(amount != null && amount.signum() > 0 && amount.stripTrailingZeros().scale() <= 2
                && amount.precision() - amount.scale() <= 17, "BILLING_TRANSACTION_INVALID_AMOUNT");
        require(command.idempotencyKey() != null && !command.idempotencyKey().isBlank()
                && command.idempotencyKey().length() <= 120, "BILLING_IDEMPOTENCY_KEY_REQUIRED");
        require(command.reason() != null && !command.reason().isBlank() && command.reason().length() <= 500,
                "BILLING_REFUND_REASON_REQUIRED");
        require(command.paymentMethod() == PaymentMethod.CASH || command.paymentMethod() == PaymentMethod.TRANSFER,
                "BILLING_INVALID_REFUND_METHOD");
    }

    private static void require(boolean valid, String code) {
        if (!valid) throw new BillingRuleException(code, code);
    }
}
