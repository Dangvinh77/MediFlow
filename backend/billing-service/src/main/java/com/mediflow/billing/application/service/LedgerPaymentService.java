package com.mediflow.billing.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.dto.response.LedgerPaymentDTO;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.ClearanceGrantedPayload;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.PaymentCompletedPayload;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.in.ProcessLedgerPaymentUseCase;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.*;

/** Receipts for each completed installment; clearance only after full payment of the exact request. */
public class LedgerPaymentService implements ProcessLedgerPaymentUseCase {
    private final LedgerPaymentRepositoryPort repository;
    private final LedgerEventPort events;
    private final LedgerPaymentMapper mapper;
    private final Clock clock;

    public LedgerPaymentService(LedgerPaymentRepositoryPort repository, LedgerEventPort events,
                                LedgerPaymentMapper mapper, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public LedgerPaymentDTO complete(UUID requestId, CompleteLedgerPaymentRequest command,
                                     UUID actorAccountId, String correlationId) {
        validate(command, actorAccountId, correlationId);
        repository.lockIdempotencyKey(command.idempotencyKey());
        // The account lock serializes payments and allocations across all requests in an episode.
        var context = repository.lockRequest(requestId);
        var previous = repository.findByIdempotencyKey(command.idempotencyKey());
        if (previous.isPresent()) {
            var recorded = previous.get();
            var payment = recorded.transaction();
            require(requestId.equals(payment.getPaymentRequestId())
                    && actorAccountId.equals(recorded.actorAccountId())
                    && payment.getTransactionType() == PaymentTransactionType.PAYMENT
                    && payment.isCompleted() && command.amount().compareTo(payment.getAmount()) == 0
                    && command.currency().equals(payment.getCurrency())
                    && command.paymentMethod().name().equals(payment.getPaymentMethod())
                    && Objects.equals(command.providerReference(), payment.getProviderReference()),
                    "BILLING_IDEMPOTENCY_CONFLICT");
            return mapper.toDTO(payment);
        }
        var request = context.request();
        var account = context.account();
        Instant now = clock.instant();
        require(account.getStatus() != AccountStatus.CLOSED && account.getStatus() != AccountStatus.SETTLED,
                "BILLING_ACCOUNT_CLOSED");
        require(request.getStatus() == PaymentRequestStatus.PENDING
                || request.getStatus() == PaymentRequestStatus.PARTIALLY_PAID, "BILLING_REQUEST_NOT_PAYABLE");
        require(request.getExpiresAt() == null || now.isBefore(request.getExpiresAt()), "BILLING_REQUEST_EXPIRED");
        require(command.currency().equals(account.getCurrency())
                && command.currency().equals(request.getCurrency()), "BILLING_CURRENCY_MISMATCH");
        require(context.completedAmount().add(command.amount()).compareTo(request.getRequestedAmount()) <= 0,
                "BILLING_PAYMENT_EXCEEDS_REQUEST");
        if (request.getPurpose() != PaymentRequestPurpose.SETTLEMENT) {
            require(context.target() != null, "BILLING_CLEARANCE_TARGET_MISSING");
            context.target().validate(ClearancePurpose.valueOf(request.getPurpose().name()),
                    account.getCareEpisodeType(), account.getCareEpisodeId());
        }
        repository.verifyRequestTargets(context);
        PaymentClassification classification = switch (request.getPurpose()) {
            case ADMISSION_DEPOSIT -> PaymentClassification.ADMISSION_DEPOSIT;
            case SETTLEMENT -> PaymentClassification.SETTLEMENT_PAYMENT;
            default -> PaymentClassification.SERVICE_PAYMENT;
        };
        var payment = PaymentTransaction.restore(UUID.randomUUID(), account.getAccountId(), requestId,
                PaymentTransactionType.PAYMENT, classification, PaymentTransactionStatus.PENDING,
                command.amount(), command.currency(), command.paymentMethod().name(), command.providerReference(),
                command.idempotencyKey(), null, null, now);
        payment.complete(now);
        boolean paid = context.completedAmount().add(command.amount()).compareTo(request.getRequestedAmount()) == 0;
        if (paid) request.markPaid(now); else request.markPartiallyPaid();
        repository.save(payment, request, actorAccountId);
        if (classification != PaymentClassification.ADMISSION_DEPOSIT) repository.allocate(payment);
        var target = context.target();
        events.appendHeld(account.getAccountId(), new LedgerIntegrationEvent(UUID.randomUUID(), "payment.completed",
                1, now, correlationId, "billing-service", new PaymentCompletedPayload(payment.getTransactionId(),
                request.getInvoiceId(), requestId, account.getAccountId(), account.getPatientId(), account.getDepartmentId(),
                account.getCareEpisodeType().name(), account.getCareEpisodeId(), classification.name(), payment.getAmount(),
                payment.getCurrency(), payment.getPaymentMethod(), now, target == null ? null : target.prescriptionId(),
                target == null ? java.util.List.of() : target.labTestIds())));
        if (paid && request.getPurpose() != PaymentRequestPurpose.SETTLEMENT) {
            var clearance = FinancialClearance.grant(account.getAccountId(), requestId, request.getInvoiceId(),
                    account.getPatientId(), ClearancePurpose.valueOf(request.getPurpose().name()), account.getCareEpisodeType(),
                    account.getCareEpisodeId(), target.appointmentId(), target.recordId(), target.labTestIds(), target.prescriptionId(),
                    target.admissionId(), target.surgeryCaseId(), request.getRequestedAmount(), request.getCurrency(),
                    payment.getPaymentMethod(), false, request.getExpiresAt(), now);
            clearance = repository.saveClearance(clearance);
            events.appendHeld(account.getAccountId(), new LedgerIntegrationEvent(UUID.randomUUID(), "financial.clearance.granted",
                    1, now, correlationId, "billing-service", new ClearanceGrantedPayload(clearance.getClearanceId(),
                    clearance.getInvoiceId(), clearance.getAccountId(), clearance.getPatientId(), clearance.getCareEpisodeType().name(),
                    clearance.getCareEpisodeId(), clearance.getPurpose().name(), clearance.getAppointmentId(), clearance.getRecordId(),
                    clearance.getLabTestIds(), clearance.getPrescriptionId(), clearance.getAdmissionId(), clearance.getSurgeryCaseId(),
                    clearance.getAmount(), clearance.getCurrency(), clearance.getPaymentMethod(), clearance.getExpiresAt(), false)));
        }
        return mapper.toDTO(payment);
    }

    private static void validate(CompleteLedgerPaymentRequest command, UUID actor, String correlationId) {
        require(command != null && actor != null && correlationId != null && !correlationId.isBlank()
                && correlationId.length() <= 120, "BILLING_PAYMENT_CONTEXT_REQUIRED");
        BigDecimal amount = command.amount();
        require(amount != null && amount.signum() > 0 && amount.stripTrailingZeros().scale() <= 2
                && amount.precision() - amount.scale() <= 17, "BILLING_TRANSACTION_INVALID_AMOUNT");
        require(command.idempotencyKey() != null && !command.idempotencyKey().isBlank()
                && command.idempotencyKey().length() <= 120, "BILLING_IDEMPOTENCY_KEY_REQUIRED");
        require(command.currency() != null && command.currency().matches("[A-Z]{3}")
                && command.paymentMethod() != null, "BILLING_INVALID_PAYMENT_METHOD");
        require(command.paymentMethod() != PaymentMethod.INSURANCE, "BILLING_INSURANCE_IS_NOT_CASH");
        require(command.providerReference() == null || command.providerReference().length() <= 120,
                "BILLING_INVALID_PROVIDER_REFERENCE");
    }

    private static void require(boolean valid, String code) {
        if (!valid && "BILLING_IDEMPOTENCY_CONFLICT".equals(code))
            throw new com.mediflow.common.exception.DuplicateResourceException(code, code);
        if (!valid) throw new BillingRuleException(code, code);
    }
}
