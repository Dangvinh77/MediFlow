package com.mediflow.billing.application.dto.command;

import com.mediflow.billing.domain.model.PaymentMethod;
import java.math.BigDecimal;
import java.util.UUID;

/** Internal form of the V2 payment command after the decimal-string wire amount is parsed. */
public record ScheduledPaymentCommand(UUID reservationId, long expectedReservationRevision,
                                      BigDecimal amount, String currency, PaymentMethod paymentMethod,
                                      String providerReference, UUID quoteId, String basis) {
    public ScheduledPaymentCommand(UUID reservationId, long expectedReservationRevision,
                                      BigDecimal amount, String currency, PaymentMethod paymentMethod,
                                      String providerReference) {
        this(reservationId, expectedReservationRevision, amount, currency, paymentMethod, providerReference, null, "PATIENT_PAYMENT");
    }
    public static ScheduledPaymentCommand fromWire(com.mediflow.billing.application.dto.request.ScheduledPaymentRequest request) {
        PaymentMethod method = switch (request.paymentMethod()) {
            case "CASH" -> PaymentMethod.CASH;
            case "BANK_TRANSFER" -> PaymentMethod.TRANSFER;
            default -> throw new com.mediflow.common.exception.BusinessRuleException("BILLING_INVALID_PAYMENT_METHOD", "Invalid payment method");
        };
        try {
            return new ScheduledPaymentCommand(request.reservationId(), request.expectedReservationRevision(),
                    new BigDecimal(request.amount()), request.currency(), method, request.providerReference(), request.quoteId(), "PATIENT_PAYMENT");
        } catch (NumberFormatException invalid) {
            throw new com.mediflow.common.exception.BusinessRuleException("BILLING_TRANSACTION_INVALID_AMOUNT", "Invalid amount");
        }
    }
    public static ScheduledPaymentCommand zero(UUID reservationId, int expectedRevision, UUID quoteId) {
        return new ScheduledPaymentCommand(reservationId, expectedRevision, BigDecimal.ZERO, "VND", PaymentMethod.CASH,
                null, quoteId, "ZERO_LIABILITY");
    }
 }
