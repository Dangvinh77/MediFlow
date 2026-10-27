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
import com.mediflow.billing.application.event.OutpatientFinancialEligibilityGrantedEvent;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort;
import com.mediflow.billing.application.port.out.LabQuotedPaymentRepositoryPort;
import com.mediflow.billing.application.port.out.ScheduledEligibilityEventPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.*;
import com.mediflow.billing.domain.model.LabQuotedPaymentReceipt;

/** Receipts for each completed installment; clearance only after full payment of the exact request. */
public class LedgerPaymentService {
    private final LedgerPaymentRepositoryPort repository;
    private final LedgerEventPort events;
    private final LedgerPaymentMapper mapper;
    private final Clock clock;
    private final ScheduledEligibilityEventPort scheduledEligibilityEvents;
    private final LabQuotedPaymentRepositoryPort labQuotedPayments;

    public LedgerPaymentService(LedgerPaymentRepositoryPort repository, LedgerEventPort events,
                                LedgerPaymentMapper mapper, Clock clock) {
        this(repository, events, mapper, clock, null, null);
    }

    public LedgerPaymentService(LedgerPaymentRepositoryPort repository, LedgerEventPort events,
                                LedgerPaymentMapper mapper, Clock clock,
                                ScheduledEligibilityEventPort scheduledEligibilityEvents) {
        this(repository, events, mapper, clock, scheduledEligibilityEvents, null);
    }

    public LedgerPaymentService(LedgerPaymentRepositoryPort repository, LedgerEventPort events,
                                LedgerPaymentMapper mapper, Clock clock,
                                ScheduledEligibilityEventPort scheduledEligibilityEvents,
                                LabQuotedPaymentRepositoryPort labQuotedPayments) {
        this.repository = repository;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
        this.scheduledEligibilityEvents = scheduledEligibilityEvents;
        this.labQuotedPayments = labQuotedPayments;
    }

    public LedgerPaymentDTO complete(UUID requestId, CompleteLedgerPaymentRequest command,
                                     UUID actorAccountId, String correlationId) {
        validate(command, actorAccountId, correlationId);
        require(!repository.isScheduledPaymentRequest(requestId), "BILLING_PAYMENT_FLOW_REQUIRED");
        repository.lockIdempotencyKey(command.idempotencyKey());
        // The account lock serializes payments and allocations across all requests in an episode.
        var context = repository.lockRequest(requestId);
        if (labQuotedPayments != null && labQuotedPayments.isQuoteBoundLabRequest(requestId)) {
            throw new BillingRuleException("BILLING_QUOTE_PAYMENT_REQUIRED",
                    "Quote-bound outpatient Lab requests require the V2 Lab payment route");
        }
        return completeLocked(requestId, command, actorAccountId, correlationId, context,
                repository.findByIdempotencyKey(command.idempotencyKey()));
    }

    /** V2 Lab route: quote/source validation, cash ledger writes and immutable evidence share one transaction. */
    public LedgerPaymentDTO completeLabQuoted(UUID requestId, UUID quoteId, CompleteLedgerPaymentRequest command,
                                               UUID actorAccountId, String correlationId) {
        validate(command, actorAccountId, correlationId);
        if (labQuotedPayments == null) {
            throw new BillingRuleException("BILLING_QUOTE_CONFIGURATION_REQUIRED", "Outpatient quote payments are disabled");
        }
        require(requestId != null && quoteId != null, "BILLING_PAYMENT_CONTEXT_REQUIRED");
        require(command.paymentMethod() == PaymentMethod.CASH || command.paymentMethod() == PaymentMethod.TRANSFER,
                "BILLING_INVALID_PAYMENT_METHOD");
        require(!repository.isScheduledPaymentRequest(requestId), "BILLING_PAYMENT_FLOW_REQUIRED");
        repository.lockIdempotencyKey(command.idempotencyKey());
        labQuotedPayments.lockLabSource(requestId);
        var context = repository.lockRequest(requestId);
        var priorPayment = repository.findByIdempotencyKey(command.idempotencyKey());
        if (priorPayment.isPresent()) {
            var receipt = labQuotedPayments.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> idempotencyConflict());
            var payment = priorPayment.get().transaction();
            String expectedCommandFingerprint = commandFingerprint(requestId, quoteId, actorAccountId,
                    command, receipt.quoteFingerprint());
            boolean exactReplay = requestId.equals(payment.getPaymentRequestId())
                    && requestId.equals(receipt.paymentRequestId()) && quoteId.equals(receipt.quoteId())
                    && actorAccountId.equals(priorPayment.get().actorAccountId())
                    && actorAccountId.equals(receipt.actorAccountId())
                    && command.idempotencyKey().equals(receipt.idempotencyKey())
                    && command.amount().compareTo(receipt.amount()) == 0
                    && command.currency().equals(receipt.currency())
                    && command.paymentMethod() == receipt.paymentMethod()
                    && expectedCommandFingerprint.equals(receipt.commandFingerprint())
                    && payment.getTransactionId().equals(receipt.transactionId())
                    && payment.getCompletedAt().equals(receipt.completedAt())
                    && receipt.clearanceId() != null
                    && payment.getProviderReference() == null
                    && payment.getAmount().compareTo(command.amount()) == 0
                    && payment.getCurrency().equals(command.currency())
                    && command.paymentMethod().name().equals(payment.getPaymentMethod())
                    && payment.getTransactionType() == PaymentTransactionType.PAYMENT && payment.isCompleted();
            require(exactReplay, "BILLING_IDEMPOTENCY_CONFLICT");
            return mapper.toDTO(payment);
        }
        if (labQuotedPayments.findByIdempotencyKey(command.idempotencyKey()).isPresent()) {
            throw idempotencyConflict();
        }
        if (labQuotedPayments.findByPaymentRequestId(requestId).isPresent()) {
            throw new BillingRuleException("BILLING_REQUEST_NOT_PAYABLE", "Lab payment request already has a quoted receipt");
        }
        var quote = labQuotedPayments.requireCurrentPayableQuote(requestId, context, quoteId,
                command.amount(), clock.instant());
        String quoteFingerprint = quoteFingerprint(quote);
        String commandFingerprint = commandFingerprint(requestId, quoteId, actorAccountId, command, quoteFingerprint);
        var payment = completeLocked(requestId, command, actorAccountId, correlationId, context, java.util.Optional.empty());
        var receipt = new LabQuotedPaymentReceipt(requestId, payment.transactionId(), quoteId, actorAccountId,
                command.idempotencyKey(), command.amount(), command.currency(), command.paymentMethod(),
                quoteFingerprint, commandFingerprint, labQuotedPayments.requireClearanceId(requestId), payment.completedAt());
        labQuotedPayments.append(receipt);
        return payment;
    }

    private LedgerPaymentDTO completeLocked(UUID requestId, CompleteLedgerPaymentRequest command,
            UUID actorAccountId, String correlationId, LedgerPaymentRepositoryPort.PaymentContext context,
            java.util.Optional<LedgerPaymentRepositoryPort.RecordedPayment> previous) {
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
        // Persist and return the same receipt timestamp on the first request and its replay.
        // Keep the original clock instant for expiry checks and clearance authority.
        Instant completedAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        payment.complete(completedAt);
        boolean paid = context.completedAmount().add(command.amount()).compareTo(request.getRequestedAmount()) == 0;
        if (paid) request.markPaid(now); else request.markPartiallyPaid();
        repository.save(payment, request, actorAccountId);
        if (classification != PaymentClassification.ADMISSION_DEPOSIT) repository.allocate(payment);
        var target = context.target();
        events.appendForOutpatientRequest(account.getAccountId(), requestId, new LedgerIntegrationEvent(UUID.randomUUID(), "payment.completed",
                1, now, correlationId, "billing-service", new PaymentCompletedPayload(payment.getTransactionId(),
                request.getInvoiceId(), requestId, account.getAccountId(), account.getPatientId(), account.getDepartmentId(),
                account.getCareEpisodeType().name(), account.getCareEpisodeId(), classification.name(), payment.getAmount(),
                payment.getCurrency(), payment.getPaymentMethod(), completedAt, target == null ? null : target.prescriptionId(),
                target == null ? java.util.List.of() : target.labTestIds())));
        if (paid && request.getPurpose() != PaymentRequestPurpose.SETTLEMENT) {
            var clearance = FinancialClearance.grant(account.getAccountId(), requestId, request.getInvoiceId(),
                    account.getPatientId(), ClearancePurpose.valueOf(request.getPurpose().name()), account.getCareEpisodeType(),
                    account.getCareEpisodeId(), target.appointmentId(), target.recordId(), target.labTestIds(), target.prescriptionId(),
                    target.admissionId(), target.surgeryCaseId(), request.getRequestedAmount(), request.getCurrency(),
                    payment.getPaymentMethod(), false, request.getExpiresAt(), now);
            clearance = repository.saveClearance(clearance);
            events.appendForOutpatientRequest(account.getAccountId(), requestId, new LedgerIntegrationEvent(UUID.randomUUID(), "financial.clearance.granted",
                    1, now, correlationId, "billing-service", new ClearanceGrantedPayload(clearance.getClearanceId(),
                    clearance.getInvoiceId(), clearance.getAccountId(), clearance.getPatientId(), clearance.getCareEpisodeType().name(),
                    clearance.getCareEpisodeId(), clearance.getPurpose().name(), clearance.getAppointmentId(), clearance.getRecordId(),
                    clearance.getLabTestIds(), clearance.getPrescriptionId(), clearance.getAdmissionId(), clearance.getSurgeryCaseId(),
                    clearance.getAmount(), clearance.getCurrency(), clearance.getPaymentMethod(), clearance.getExpiresAt(), false)));
        }
        return mapper.toDTO(payment);
    }

    private static String quoteFingerprint(OutpatientQuote quote) {
        var parts = new java.util.ArrayList<String>();
        parts.add(quote.quoteId().toString());
        parts.add(quote.patientId().toString());
        parts.add(quote.careEpisodeType());
        parts.add(quote.careEpisodeId().toString());
        parts.add(quote.issuedAt().toString());
        parts.add(quote.expiresAt().toString());
        quote.lines().stream().sorted(java.util.Comparator.comparing((OutpatientQuoteLine line) -> line.sourceType())
                .thenComparing(line -> line.sourceId().toString()).thenComparing(OutpatientQuoteLine::priceCode)).forEach(line -> {
            parts.add(line.sourceId().toString()); parts.add(line.sourceType()); parts.add(line.priceCode());
            parts.add(line.catalogueVersion()); parts.add(line.policyVersion()); parts.add(line.description());
            parts.add(line.quantity().toPlainString()); parts.add(line.unitPrice().toPlainString());
            parts.add(line.grossAmount().toPlainString()); parts.add(line.supportRate().toPlainString());
            parts.add(line.supportAmount().toPlainString()); parts.add(line.patientPayable().toPlainString());
        });
        return fingerprint(parts);
    }

    private static String commandFingerprint(UUID requestId, UUID quoteId, UUID actor,
            CompleteLedgerPaymentRequest command, String quoteFingerprint) {
        return fingerprint(java.util.List.of(requestId.toString(), quoteId.toString(), actor.toString(),
                command.idempotencyKey(), command.amount().stripTrailingZeros().toPlainString(), command.currency(),
                command.paymentMethod().name(), quoteFingerprint));
    }

    private static String fingerprint(java.util.List<String> values) {
        try {
            var canonical = new StringBuilder();
            for (String value : values) canonical.append(value.length()).append(':').append(value);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    private static com.mediflow.common.exception.DuplicateResourceException idempotencyConflict() {
        return new com.mediflow.common.exception.DuplicateResourceException("BILLING_IDEMPOTENCY_CONFLICT",
                "Idempotency key is already bound to a different Lab quote payment");
    }

    /**
     * C03 local commit path. The caller must have persisted the exact Clinical lease and hold the
     * scheduled operation row in this same Billing transaction. This method deliberately omits
     * the V1 request-expiry check and the V1 clearance event; only the new eligibility fact is
     * written for Clinical, while the historical cash evidence remains held in the outbox.
     */
    public ScheduledLedgerReceipt completeScheduled(ScheduledPaymentOperation operation, Instant now) {
        require(operation != null && operation.status() == PaymentOperationStatus.PREPARING
                && operation.hasLease() && operation.fenceToken() != null
                && operation.sourceType().equals("EXAM") && operation.sourceId().equals(operation.registrationId())
                && operation.careEpisodeId().equals(operation.registrationId()), "BILLING_PAYMENT_CONTEXT_REQUIRED");
        if (scheduledEligibilityEvents == null) {
            throw new IllegalStateException("Scheduled eligibility outbox is not configured");
        }
        String ledgerKey = "scheduled:" + operation.paymentOperationId();
        var command = new CompleteLedgerPaymentRequest(ledgerKey, operation.amount(), operation.currency(),
                operation.paymentMethod(), operation.providerReference());
        boolean zero = "ZERO_LIABILITY".equals(operation.basis());
        if (zero) require(operation.amount().signum() == 0 && operation.quoteId() != null, "BILLING_ZERO_ELIGIBILITY_INVALID");
        else validate(command, operation.actorAccountId(), operation.correlationId());
        repository.lockIdempotencyKey(ledgerKey);
        var context = repository.lockRequest(operation.paymentRequestId());
        if (repository.findByIdempotencyKey(ledgerKey).isPresent()) {
            // Operation and ledger writes share one DB transaction. A prior ledger row without a
            // completed operation is therefore corruption, never permission to synthesize a receipt.
            throw new BillingRuleException("BILLING_SCHEDULED_PAYMENT_RECEIPT_CONFLICT",
                    "Scheduled ledger receipt exists without its completed operation");
        }
        var request = context.request();
        var account = context.account();
        require(account.getStatus() != AccountStatus.CLOSED && account.getStatus() != AccountStatus.SETTLED,
                "BILLING_ACCOUNT_CLOSED");
        require(request.getPurpose() == PaymentRequestPurpose.EXAM
                && request.getStatus() == PaymentRequestStatus.PENDING, "BILLING_REQUEST_NOT_PAYABLE");
        require("OUTPATIENT_VISIT".equals(account.getCareEpisodeType().name())
                && operation.registrationId().equals(account.getCareEpisodeId())
                && operation.patientId().equals(account.getPatientId()), "BILLING_PAYMENT_SOURCE_CONFLICT");
        require(operation.currency().equals(account.getCurrency()) && operation.currency().equals(request.getCurrency()),
                "BILLING_CURRENCY_MISMATCH");
        require(context.completedAmount().signum() == 0
                && request.getRequestedAmount().compareTo(operation.amount()) == 0,
                "BILLING_SCHEDULED_PAYMENT_MUST_BE_FULL_AMOUNT");
        var target = context.target();
        require(target != null && operation.registrationId().equals(target.recordId())
                && target.appointmentId() == null, "BILLING_PAYMENT_SOURCE_CONFLICT");
        repository.verifyRequestTargets(context);

        PaymentTransaction payment = null;
        Instant completedAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (!zero) {
        payment = PaymentTransaction.restore(UUID.randomUUID(), account.getAccountId(), request.getPaymentRequestId(),
                PaymentTransactionType.PAYMENT, PaymentClassification.SERVICE_PAYMENT, PaymentTransactionStatus.PENDING,
                operation.amount(), operation.currency(), operation.paymentMethod().name(), operation.providerReference(),
                ledgerKey, null, null, now);
        payment.complete(completedAt);
        request.markPaid(now);
        repository.save(payment, request, operation.actorAccountId());
        repository.allocate(payment);
        events.appendHeld(account.getAccountId(), new LedgerIntegrationEvent(UUID.randomUUID(), "payment.completed",
                1, now, operation.correlationId(), "billing-service", new PaymentCompletedPayload(
                payment.getTransactionId(), request.getInvoiceId(), request.getPaymentRequestId(), account.getAccountId(),
                account.getPatientId(), account.getDepartmentId(), account.getCareEpisodeType().name(),
                account.getCareEpisodeId(), PaymentClassification.SERVICE_PAYMENT.name(), payment.getAmount(),
                payment.getCurrency(), payment.getPaymentMethod(), completedAt, null, java.util.List.of())));

        } else {
            request.markPaid(completedAt);
            repository.saveZeroLiability(request, operation.actorAccountId());
        }
        var clearance = FinancialClearance.grant(account.getAccountId(), request.getPaymentRequestId(), request.getInvoiceId(),
                account.getPatientId(), ClearancePurpose.EXAM, account.getCareEpisodeType(), account.getCareEpisodeId(),
                null, operation.registrationId(), java.util.List.of(), null, null, null, operation.amount(),
                // Booking/quote TTL only limits collection, not an already settled registration.
                operation.currency(), zero ? "ZERO_LIABILITY" : operation.paymentMethod().name(), false, null, now);
        clearance = repository.saveClearance(clearance);
        var eligibility = new OutpatientFinancialEligibilityGrantedEvent(UUID.randomUUID(),
                "outpatient.financial.eligibility.granted", 1, completedAt, operation.correlationId(), "billing-service",
                new OutpatientFinancialEligibilityGrantedEvent.EligibilityFact(operation.paymentOperationId(),
                        request.getPaymentRequestId(), operation.registrationId(), operation.reservationId(),
                        operation.fenceToken(), account.getPatientId(), operation.sourceType(), operation.sourceId(),
                        account.getCareEpisodeType().name(), account.getCareEpisodeId(), payment == null ? null : payment.getTransactionId(),
                        clearance.getClearanceId(), operation.amount().toPlainString(), operation.currency(), completedAt,
                        operation.basis(), operation.quoteId()));
        scheduledEligibilityEvents.appendHeld(eligibility);
        return new ScheduledLedgerReceipt(payment == null ? null : mapper.toDTO(payment), clearance.getClearanceId(), completedAt);
    }

    public record ScheduledLedgerReceipt(LedgerPaymentDTO payment, UUID clearanceId, Instant completedAt) { }

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
