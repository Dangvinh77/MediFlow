package com.mediflow.clinical.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.dto.authority.PaymentRecoveryOperation;
import com.mediflow.clinical.application.dto.authority.ScheduledPaymentSnapshot;
import com.mediflow.clinical.application.dto.request.PaymentResolutionCommand;
import com.mediflow.clinical.application.port.out.OutpatientPaymentRecoveryPort;
import com.mediflow.clinical.application.port.out.ScheduledPaymentReconciliationPort;
import com.mediflow.clinical.domain.model.OutpatientPaymentOutcome;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;

/** Closes or resolves a held seat through Billing's serialized payment-operation boundary. */
@Component
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class OutpatientPaymentRecoveryWorker {
    private static final Logger log = LoggerFactory.getLogger(OutpatientPaymentRecoveryWorker.class);
    private static final int CLAIM_LIMIT = 10;
    private static final long CLAIM_LEASE_SECONDS = 45;
    private static final long ALERT_AFTER_SECONDS = 300;
    private static final String RECONCILIATION_REASON = "RESERVATION_RECONCILIATION";

    private final OutpatientPaymentRecoveryPort recovery;
    private final ScheduledPaymentReconciliationPort billing;
    private final OutpatientRegistrationApplicationService registrations;
    private final Clock clock;

    public OutpatientPaymentRecoveryWorker(OutpatientPaymentRecoveryPort recovery,
            ScheduledPaymentReconciliationPort billing,
            OutpatientRegistrationApplicationService registrations,
            @Qualifier("clinicalSchedulingClock") Clock clock) {
        this.recovery = recovery;
        this.billing = billing;
        this.registrations = registrations;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${mediflow.clinical.payment-recovery.poll-interval:30000}")
    public void poll() {
        runOnce(UUID.randomUUID().toString());
    }

    /** One bounded pass, also exposed for deterministic worker tests. */
    public void runOnce(String correlationId) {
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > 120) {
            throw new IllegalArgumentException("A bounded reconciliation correlation id is required");
        }
        Instant now = clock.instant();
        UUID owner = UUID.randomUUID();
        List<PaymentRecoveryOperation> claimed = recovery.claimDue(owner, now,
                now.plusSeconds(CLAIM_LEASE_SECONDS), CLAIM_LIMIT);
        for (PaymentRecoveryOperation operation : claimed) {
            try {
                reconcile(operation, correlationId);
                recovery.complete(operation);
            } catch (RuntimeException failure) {
                Instant failedAt = clock.instant();
                Instant retryAt = failedAt.plusSeconds(retryDelaySeconds(operation.attemptCount()));
                boolean alert = recovery.defer(operation, failedAt, retryAt, errorCode(failure));
                if (alert) {
                    log.error("Clinical payment operation {} remains locked after five minutes; retry scheduled at {}",
                            operation.paymentOperationId(), retryAt);
                } else {
                    log.warn("Clinical payment operation {} reconciliation failed; retry scheduled at {} ({})",
                            operation.paymentOperationId(), retryAt, errorCode(failure));
                }
            }
        }
    }

    private void reconcile(PaymentRecoveryOperation local, String correlationId) {
        validateLocal(local);
        ScheduledPaymentSnapshot beforeClose = billing.lookup(local.paymentOperationId(), local.registrationId(),
                local.reservationId(), correlationId);
        validateSnapshotBinding(local, beforeClose);

        // This call takes Billing's operation row lock, so a late payment commit and close cannot both win.
        ScheduledPaymentSnapshot terminal = billing.closeIfUnpaid(local.paymentOperationId(), local.registrationId(),
                local.reservationId(), local.fenceToken(), RECONCILIATION_REASON, correlationId);
        validateSnapshotBinding(local, terminal);
        ScheduledPaymentSnapshot.Operation operation = terminal.operation();
        if ("COMPLETED".equals(operation.status())) {
            registrations.resolvePayment(new PaymentResolutionCommand(local.paymentOperationId(),
                    local.paymentRequestId(), local.registrationId(), local.reservationId(), local.fenceToken(),
                    OutpatientPaymentOutcome.COMPLETED, operation.transactionId(), terminal.clearanceId(),
                    operation.completedAt(), local.basis(), local.quoteId()));
        } else if ("CLOSED_NOT_PAID".equals(operation.status())) {
            registrations.resolvePayment(new PaymentResolutionCommand(local.paymentOperationId(),
                    local.paymentRequestId(), local.registrationId(), local.reservationId(), local.fenceToken(),
                    OutpatientPaymentOutcome.CLOSED_NOT_PAID, null, null, null, local.basis(), local.quoteId()));
        } else {
            throw new IllegalStateException("Billing did not return a terminal operation after close-if-unpaid");
        }
    }

    private static void validateLocal(PaymentRecoveryOperation local) {
        if (local == null || local.paymentOperationId() == null || local.paymentRequestId() == null
                || local.registrationId() == null || local.reservationId() == null || local.patientId() == null
                || local.fenceToken() == null || local.fenceToken() < 1 || local.reconcileAfter() == null
                || local.firstAttemptAt() == null || local.recoveryOwner() == null
                || !"EXAM".equals(local.sourceType()) || !local.registrationId().equals(local.sourceId())
                || !local.registrationId().equals(local.careEpisodeId())
                || !("PATIENT_PAYMENT".equals(local.basis()) || "ZERO_LIABILITY".equals(local.basis()))
                || "ZERO_LIABILITY".equals(local.basis()) && local.quoteId() == null) {
            throw new IllegalStateException("Clinical payment recovery receipt is incomplete");
        }
    }

    private static void validateSnapshotBinding(PaymentRecoveryOperation local, ScheduledPaymentSnapshot snapshot) {
        if (snapshot == null || snapshot.operation() == null) {
            throw new IllegalStateException("Billing returned no scheduled payment operation");
        }
        ScheduledPaymentSnapshot.Operation operation = snapshot.operation();
        if (!local.paymentOperationId().equals(operation.paymentOperationId())
                || !local.paymentRequestId().equals(operation.paymentRequestId())
                || !local.registrationId().equals(operation.registrationId())
                || !local.reservationId().equals(operation.reservationId())
                || !java.util.Objects.equals(local.basis(), operation.basis())
                || !java.util.Objects.equals(local.quoteId(), operation.quoteId())
                || !"VND".equals(operation.currency())
                || operation.createdAt() == null || operation.updatedAt() == null
                || !List.of("PREPARING", "COMPLETED", "CLOSED_NOT_PAID").contains(operation.status())) {
            throw new IllegalStateException("Billing operation binding or state does not match the local lease");
        }
        BigDecimal amount = parseAmount(operation.amount());
        if ("PATIENT_PAYMENT".equals(local.basis()) && amount.signum() <= 0
                || "ZERO_LIABILITY".equals(local.basis()) && (amount.signum() != 0 || local.quoteId() == null)) {
            throw new IllegalStateException("Billing amount does not match the payment basis");
        }
        if ("PREPARING".equals(operation.status()) || "CLOSED_NOT_PAID".equals(operation.status())) {
            if (operation.transactionId() != null || operation.completedAt() != null
                    || snapshot.payment() != null || snapshot.clearanceId() != null) {
                throw new IllegalStateException("Billing non-completed operation contains payment evidence");
            }
            return;
        }
        if (operation.completedAt() == null || snapshot.clearanceId() == null) {
            throw new IllegalStateException("Billing completed operation is missing its completion receipt");
        }
        if ("ZERO_LIABILITY".equals(local.basis())) {
            if (operation.transactionId() != null || snapshot.payment() != null) {
                throw new IllegalStateException("Zero-liability operation must not contain a payment transaction");
            }
            return;
        }
        ScheduledPaymentSnapshot.Payment payment = snapshot.payment();
        if (operation.transactionId() == null || payment == null
                || !operation.transactionId().equals(payment.transactionId())
                || !"COMPLETED".equals(payment.status())
                || !operation.completedAt().equals(payment.completedAt())
                || !operation.amount().equals(payment.amount())
                || !operation.currency().equals(payment.currency())
                || !("CASH".equals(payment.paymentMethod()) || "BANK_TRANSFER".equals(payment.paymentMethod()))) {
            throw new IllegalStateException("Billing payment and operation completion receipts differ");
        }
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalStateException("Billing amount is missing");
        try {
            BigDecimal amount = new BigDecimal(raw);
            if (amount.scale() > 2 || amount.precision() - amount.scale() > 17) {
                throw new IllegalStateException("Billing amount has an invalid decimal representation");
            }
            return amount;
        } catch (NumberFormatException malformed) {
            throw new IllegalStateException("Billing amount is malformed", malformed);
        }
    }

    private static long retryDelaySeconds(int attemptCount) {
        return attemptCount <= 1 ? 30 : 60;
    }

    private static String errorCode(RuntimeException failure) {
        if (failure instanceof UpstreamUnavailableException unavailable) return unavailable.getCode();
        if (failure instanceof feign.FeignException http) return "BILLING_HTTP_" + http.status();
        String name = failure.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_.-]", "");
        String value = name.isBlank() ? "RECONCILIATION_FAILURE" : name;
        return value.substring(0, Math.min(120, value.length()));
    }
}
