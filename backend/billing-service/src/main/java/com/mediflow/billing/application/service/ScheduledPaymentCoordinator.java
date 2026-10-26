package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.ScheduledPaymentCommand;
import com.mediflow.billing.application.dto.response.ScheduledPaymentResult;
import com.mediflow.billing.application.port.out.ClinicalPaymentLockPort;
import com.mediflow.billing.application.port.out.ClinicalPaymentLockPort.PaymentAcquireRejectedException;
import com.mediflow.billing.application.port.out.ScheduledPaymentTransactionsPort;
import com.mediflow.billing.application.port.out.ScheduledPaymentTransactionsPort.ClosePaymentCommand;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.exception.ScheduledPaymentOperationNotFoundException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.billing.domain.model.PaymentOperationStatus;
import com.mediflow.billing.domain.model.ScheduledPaymentLease;
import com.mediflow.billing.domain.model.ScheduledPaymentOperation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Coordinates separate durable Billing transactions and idempotent Clinical calls. */
@Service
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.scheduled-payments.enabled"},
        havingValue = "true")
public final class ScheduledPaymentCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ScheduledPaymentCoordinator.class);
    private static final List<String> DEFINITIVE_ACQUIRE_REFUSALS =
            List.of("RESERVATION_NOT_PAYABLE", "REVISION_CONFLICT", "PAYMENT_OPERATION_CLOSED", "IDEMPOTENCY_CONFLICT");

    private final ScheduledPaymentTransactionsPort transactions;
    private final ClinicalPaymentLockPort clinical;
    private final Clock clock;

    public ScheduledPaymentCoordinator(ScheduledPaymentTransactionsPort transactions,
                                       ClinicalPaymentLockPort clinical) {
        this(transactions, clinical, Clock.systemUTC());
    }

    @Autowired
    public ScheduledPaymentCoordinator(ScheduledPaymentTransactionsPort transactions,
                                       ClinicalPaymentLockPort clinical, Clock clock) {
        this.transactions = transactions;
        this.clinical = clinical;
        this.clock = clock;
    }

    public ScheduledPaymentResult pay(UUID paymentRequestId, ScheduledPaymentCommand command,
                                      UUID actorAccountId, String idempotencyKey, String correlationId,
                                      Instant now) {
        validate(paymentRequestId, command, actorAccountId, idempotencyKey, correlationId);
        String fingerprint = fingerprint(paymentRequestId, command);
        var operation = transactions.prepare(paymentRequestId, command, actorAccountId,
                idempotencyKey, fingerprint, correlationId, now);
        return advance(operation, correlationId, now);
    }

    public ScheduledPaymentResult closeIfUnpaid(UUID operationId, ClosePaymentCommand command, Instant now) {
        if (operationId == null || command == null || command.registrationId() == null
                || command.reservationId() == null || command.fenceToken() <= 0
                || !"RESERVATION_RECONCILIATION".equals(command.reason())) {
            throw new BillingRuleException("BILLING_PAYMENT_CONTEXT_REQUIRED", "Close binding is required");
        }
        return transactions.closeIfUnpaid(operationId, command, now);
    }

    public ScheduledPaymentResult current(UUID operationId) {
        return transactions.currentResult(operationId);
    }

    public ScheduledPaymentResult currentForHuman(UUID operationId, UUID actorAccountId) {
        var operation = transactions.findOperation(operationId);
        if (!operation.actorAccountId().equals(actorAccountId)) {
            throw new ScheduledPaymentOperationNotFoundException(operationId);
        }
        return transactions.currentResult(operationId);
    }

    public ScheduledPaymentResult currentForClinical(UUID operationId, UUID registrationId, UUID reservationId) {
        var operation = transactions.findOperation(operationId);
        if (!operation.registrationId().equals(registrationId) || !operation.reservationId().equals(reservationId)) {
            throw new ScheduledPaymentOperationNotFoundException(operationId);
        }
        return transactions.currentResult(operationId);
    }

    /** Called by the durable worker after restart; every retry uses the same operation and lease. */
    public void recoverPending(Instant now, int limit) {
        for (var operation : transactions.recoverable(limit)) {
            try {
                advance(operation, operation.correlationId(), now);
            } catch (RuntimeException failure) {
                log.warn("Scheduled payment recovery failed for operation {}", operation.paymentOperationId(), failure);
            }
        }
    }

    private ScheduledPaymentResult advance(ScheduledPaymentOperation initial, String correlationId, Instant now) {
        var operation = initial;
        if (operation.status() == PaymentOperationStatus.CLOSED_NOT_PAID) {
            throw new DuplicateResourceException("PAYMENT_OPERATION_CLOSED", "Payment operation is closed");
        }
        if (operation.status() == PaymentOperationStatus.COMPLETED) {
            deliverResolution(operation, correlationId, now);
            return transactions.currentResult(operation.paymentOperationId());
        }
        if (!operation.hasLease()) {
            try {
                ScheduledPaymentLease lease = clinical.acquire(operation);
                validateLease(operation, lease);
                operation = transactions.recordLease(operation.paymentOperationId(), lease, now);
            } catch (PaymentAcquireRejectedException rejected) {
                if (DEFINITIVE_ACQUIRE_REFUSALS.contains(rejected.errorCode())) {
                    return transactions.closeAcquireRejected(operation.paymentOperationId(), rejected.errorCode(), now);
                }
                return transactions.currentResult(operation.paymentOperationId());
            } catch (ClinicalPaymentLockPort.PaymentAuthorityUnavailableException unavailable) {
                return transactions.currentResult(operation.paymentOperationId());
            }
        }
        if (operation.status() != PaymentOperationStatus.PREPARING) {
            return transactions.currentResult(operation.paymentOperationId());
        }
        operation = transactions.complete(operation.paymentOperationId(), correlationId, clock.instant());
        if (operation.status() == PaymentOperationStatus.COMPLETED) {
            deliverResolution(operation, correlationId, now);
        }
        return transactions.currentResult(operation.paymentOperationId());
    }

    private void deliverResolution(ScheduledPaymentOperation operation, String correlationId, Instant now) {
        if (operation.resolutionAppliedAt() != null || operation.fenceToken() == null
                || operation.status() != PaymentOperationStatus.COMPLETED) return;
        try {
            clinical.resolve(new ClinicalPaymentLockPort.PaymentResolution(operation.paymentOperationId(),
                    operation.paymentRequestId(), operation.registrationId(), operation.reservationId(),
                    operation.fenceToken(), "COMPLETED", operation.transactionId(), operation.clearanceId(),
                    operation.completedAt(), operation.correlationId(), operation.basis(), operation.quoteId()));
            transactions.markResolutionApplied(operation.paymentOperationId(), now);
        } catch (ClinicalPaymentLockPort.PaymentAuthorityUnavailableException ignored) {
            // Cash is already committed with a durable outbox fact; recovery retries this exact receipt.
        }
    }

    private static void validateLease(ScheduledPaymentOperation operation, ScheduledPaymentLease lease) {
        if (lease == null || !operation.paymentOperationId().equals(lease.paymentOperationId())
                || !operation.reservationId().equals(lease.reservationId()) || lease.reservationRevision() <= 0
                || lease.reservationRevision() <= operation.expectedReservationRevision()
                || !Objects.equals(operation.basis(), lease.basis())
                || !Objects.equals(operation.quoteId(), lease.quoteId())
                || lease.fenceToken() <= 0 || lease.lockedAt() == null || lease.reconcileAfter() == null
                || !lease.reconcileAfter().equals(lease.lockedAt().plusSeconds(120))) {
            throw new ClinicalPaymentLockPort.PaymentAuthorityUnavailableException("Clinical lease receipt did not match the Billing operation");
        }
    }

    private static void validate(UUID requestId, ScheduledPaymentCommand command, UUID actor,
                                 String key, String correlation) {
        require(requestId != null && actor != null && command != null, "BILLING_PAYMENT_CONTEXT_REQUIRED");
        require(command.reservationId() != null && command.expectedReservationRevision() > 0,
                "BILLING_PAYMENT_CONTEXT_REQUIRED");
        BigDecimal amount = command.amount();
        require(amount != null && (amount.signum() > 0 && "PATIENT_PAYMENT".equals(command.basis())
                || amount.signum() == 0 && "ZERO_LIABILITY".equals(command.basis()) && command.quoteId() != null) && amount.stripTrailingZeros().scale() <= 2
                && amount.precision() - amount.scale() <= 17, "BILLING_TRANSACTION_INVALID_AMOUNT");
        require(command.currency() != null && command.currency().matches("[A-Z]{3}"), "BILLING_CURRENCY_MISMATCH");
        require(command.paymentMethod() != null && command.paymentMethod() != com.mediflow.billing.domain.model.PaymentMethod.INSURANCE,
                "BILLING_INVALID_PAYMENT_METHOD");
        require(command.paymentMethod() == com.mediflow.billing.domain.model.PaymentMethod.CASH
                        || command.paymentMethod() == com.mediflow.billing.domain.model.PaymentMethod.TRANSFER,
                "BILLING_INVALID_PAYMENT_METHOD");
        require(command.paymentMethod() == com.mediflow.billing.domain.model.PaymentMethod.CASH
                        ? command.providerReference() == null
                        : command.providerReference() != null && !command.providerReference().isBlank()
                            && command.providerReference().length() <= 120,
                "BILLING_INVALID_PROVIDER_REFERENCE");
        require(key != null && !key.isBlank() && key.length() <= 120, "BILLING_IDEMPOTENCY_KEY_REQUIRED");
        require(correlation != null && !correlation.isBlank() && correlation.length() <= 120,
                "BILLING_PAYMENT_CONTEXT_REQUIRED");
    }

    private static String fingerprint(UUID requestId, ScheduledPaymentCommand command) {
        String canonical = String.join("\n", requestId.toString(), command.reservationId().toString(),
                Long.toString(command.expectedReservationRevision()), command.amount().stripTrailingZeros().toPlainString(),
                command.currency(), command.paymentMethod().name(), Objects.toString(command.providerReference(), ""), Objects.toString(command.quoteId(), ""), command.basis());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    private static void require(boolean valid, String code) {
        if (!valid) throw new BillingRuleException(code, code);
    }
}
