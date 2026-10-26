package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.dto.request.LabQuotedPaymentRequest;
import com.mediflow.billing.application.dto.response.LedgerPaymentDTO;
import com.mediflow.billing.application.port.in.ProcessLabQuotedPaymentUseCase;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.PaymentMethod;
import java.math.BigDecimal;
import java.util.UUID;

/** Converts the V2 wire values to Billing's ledger command without widening the V1 contract. */
public final class ProcessLabQuotedPaymentService implements ProcessLabQuotedPaymentUseCase {
    private final LedgerPaymentService ledger;

    public ProcessLabQuotedPaymentService(LedgerPaymentService ledger) { this.ledger = ledger; }

    @Override public LedgerPaymentDTO complete(UUID paymentRequestId, LabQuotedPaymentRequest command,
            String idempotencyKey, UUID actorAccountId, String correlationId) {
        if (paymentRequestId == null || command == null || command.quoteId() == null || actorAccountId == null
                || idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,120}")
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120) {
            throw new BillingRuleException("BILLING_PAYMENT_CONTEXT_REQUIRED", "Payment context is required");
        }
        PaymentMethod method = switch (command.paymentMethod()) {
            case "CASH" -> PaymentMethod.CASH;
            case "BANK_TRANSFER" -> PaymentMethod.TRANSFER;
            default -> throw new BillingRuleException("BILLING_INVALID_PAYMENT_METHOD", "Payment method is not accepted");
        };
        BigDecimal amount;
        try { amount = new BigDecimal(command.amount()); }
        catch (NumberFormatException invalid) {
            throw new BillingRuleException("BILLING_TRANSACTION_INVALID_AMOUNT", "Payment amount is invalid");
        }
        var ledgerCommand = new CompleteLedgerPaymentRequest(idempotencyKey, amount, command.currency(), method, null);
        return ledger.completeLabQuoted(paymentRequestId, command.quoteId(), ledgerCommand, actorAccountId, correlationId);
    }
}
