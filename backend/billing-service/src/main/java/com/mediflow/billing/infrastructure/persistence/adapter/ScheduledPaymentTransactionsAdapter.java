package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.ScheduledPaymentCommand;
import com.mediflow.billing.application.dto.response.ScheduledPaymentResult;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort;
import com.mediflow.billing.application.port.out.ScheduledPaymentTransactionsPort;
import com.mediflow.billing.application.service.LedgerPaymentService;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.exception.LedgerPaymentRequestNotFoundException;
import com.mediflow.billing.domain.exception.ScheduledPaymentOperationNotFoundException;
import com.mediflow.billing.domain.model.PaymentOperationStatus;
import com.mediflow.billing.domain.model.PaymentRequestPurpose;
import com.mediflow.billing.domain.model.PaymentRequestStatus;
import com.mediflow.billing.domain.model.ScheduledPaymentLease;
import com.mediflow.billing.domain.model.ScheduledPaymentOperation;
import com.mediflow.common.exception.DuplicateResourceException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.scheduled-payments.enabled"},
        havingValue = "true")
public class ScheduledPaymentTransactionsAdapter implements ScheduledPaymentTransactionsPort {
    private static final String SELECT_OPERATION = "SELECT * FROM scheduled_payment_operation WHERE payment_operation_id=?";
    private final JdbcTemplate jdbc;
    private final LedgerPaymentRepositoryPort ledgerRepository;
    private final LedgerPaymentService ledgerPayments;
    private final com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort quotes;
    private final boolean quotesEnabled;
    private final boolean zeroEnabled;

    public ScheduledPaymentTransactionsAdapter(JdbcTemplate jdbc, LedgerPaymentRepositoryPort ledgerRepository,
            LedgerPaymentService ledgerPayments) {
        this(jdbc, ledgerRepository, ledgerPayments, (com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort) null, false, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ScheduledPaymentTransactionsAdapter(JdbcTemplate jdbc, LedgerPaymentRepositoryPort ledgerRepository,
            LedgerPaymentService ledgerPayments,
            org.springframework.beans.factory.ObjectProvider<com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort> quotes,
            @org.springframework.beans.factory.annotation.Value("${mediflow.billing.outpatient-quotes.enabled:false}") boolean quotesEnabled,
            @org.springframework.beans.factory.annotation.Value("${mediflow.billing.zero-payable-eligibility.enabled:false}") boolean zeroEnabled) {
        this(jdbc, ledgerRepository, ledgerPayments, quotes.getIfAvailable(), quotesEnabled, zeroEnabled);
    }

    private ScheduledPaymentTransactionsAdapter(JdbcTemplate jdbc, LedgerPaymentRepositoryPort ledgerRepository,
            LedgerPaymentService ledgerPayments, com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort quotes,
            boolean quotesEnabled, boolean zeroEnabled) {
        this.jdbc = jdbc;
        this.ledgerRepository = ledgerRepository;
        this.ledgerPayments = ledgerPayments;
        this.quotes = quotes;
        this.quotesEnabled = quotesEnabled;
        this.zeroEnabled = zeroEnabled;
        if (zeroEnabled && !quotesEnabled || quotesEnabled && quotes == null)
            throw new IllegalArgumentException("Zero eligibility requires outpatient quotes and its repository");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public ScheduledPaymentOperation prepare(UUID paymentRequestId, ScheduledPaymentCommand command,
            UUID actorAccountId, String idempotencyKey, String commandFingerprint, String correlationId, Instant now) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", idempotencyKey);
        Optional<ScheduledPaymentOperation> previous = findByIdempotencyKey(idempotencyKey, true);
        if (previous.isPresent()) {
            var recorded = previous.get();
            if (!recorded.paymentRequestId().equals(paymentRequestId)
                    || !recorded.actorAccountId().equals(actorAccountId)
                    || !recorded.commandFingerprint().equals(commandFingerprint)) {
                throw new DuplicateResourceException("BILLING_IDEMPOTENCY_CONFLICT",
                        "Idempotency key is bound to a different scheduled payment intent");
            }
            return recorded;
        }
        List<UUID> active = jdbc.queryForList("""
                SELECT payment_operation_id FROM scheduled_payment_operation
                 WHERE payment_request_id=? AND status='PREPARING' FOR UPDATE
                """, UUID.class, paymentRequestId);
        if (!active.isEmpty()) throw new DuplicateResourceException("BILLING_PAYMENT_IN_PROGRESS",
                "This payment request already has a preparing operation");

        Source source = jdbc.query("""
                SELECT registration_id,reservation_id,patient_id,source_type,source_id,care_episode_id,
                       registration_status,reservation_status,payment_request_id
                  FROM outpatient_registration_source WHERE payment_request_id=? FOR UPDATE
                """, SOURCE_MAPPER, paymentRequestId).stream().findFirst()
                .orElseThrow(() -> new LedgerPaymentRequestNotFoundException(paymentRequestId));
        if (!paymentRequestId.equals(source.paymentRequestId())) {
            throw new DuplicateResourceException("BILLING_PAYMENT_SOURCE_CONFLICT", "Payment request source binding differs");
        }
        if (!command.reservationId().equals(source.reservationId())) {
            throw new DuplicateResourceException("BILLING_PAYMENT_SOURCE_CONFLICT", "Reservation does not match the source binding");
        }
        require("AWAITING_PAYMENT".equals(source.registrationStatus()) && "HELD".equals(source.reservationStatus()),
                "BILLING_REQUEST_NOT_PAYABLE");
        var context = ledgerRepository.lockRequest(paymentRequestId);
        var request = context.request();
        var account = context.account();
        require(request.getPurpose() == PaymentRequestPurpose.EXAM
                && request.getStatus() == PaymentRequestStatus.PENDING, "BILLING_REQUEST_NOT_PAYABLE");
        require(source.patientId().equals(account.getPatientId()) && source.registrationId().equals(account.getCareEpisodeId())
                && "OUTPATIENT_VISIT".equals(account.getCareEpisodeType().name())
                && source.registrationId().equals(source.careEpisodeId()), "BILLING_PAYMENT_SOURCE_CONFLICT");
        require(request.getCurrency().equals(command.currency()) && account.getCurrency().equals(command.currency()),
                "BILLING_CURRENCY_MISMATCH");
        require(context.completedAmount().signum() == 0 && command.amount().compareTo(request.getRequestedAmount()) == 0,
                "BILLING_SCHEDULED_PAYMENT_MUST_BE_FULL_AMOUNT");
        ledgerRepository.verifyRequestTargets(context);
        if (quotesEnabled) {
            var quote = quotes.findCurrent(paymentRequestId).orElseThrow(() ->
                    new BillingRuleException("BILLING_QUOTE_REQUIRED", "Current outpatient quote is required"));
            require(quote.quoteId().equals(command.quoteId()) && quote.patientId().equals(account.getPatientId())
                    && quote.careEpisodeId().equals(account.getCareEpisodeId())
                    && now.isBefore(quote.expiresAt()) && !now.isBefore(quote.issuedAt())
                    && quote.patientPayable().compareTo(command.amount()) == 0, "BILLING_QUOTE_STALE");
        } else require(command.quoteId() == null && "PATIENT_PAYMENT".equals(command.basis()), "BILLING_QUOTES_DISABLED");
        require("PATIENT_PAYMENT".equals(command.basis()) && command.amount().signum() > 0
                || zeroEnabled && quotesEnabled && "ZERO_LIABILITY".equals(command.basis())
                    && command.amount().signum() == 0 && command.quoteId() != null, "BILLING_ZERO_ELIGIBILITY_DISABLED");

        UUID operationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO scheduled_payment_operation(payment_operation_id,payment_request_id,account_id,registration_id,
                    reservation_id,patient_id,source_type,source_id,care_episode_id,actor_account_id,idempotency_key,
                    command_fingerprint,correlation_id,expected_reservation_revision,amount,currency,payment_method,
                    provider_reference,status,created_at,updated_at,quote_id,basis)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PREPARING',?,?,?,?)
                """, operationId, paymentRequestId, account.getAccountId(), source.registrationId(), source.reservationId(),
                source.patientId(), source.sourceType(), source.sourceId(), source.careEpisodeId(), actorAccountId,
                idempotencyKey, commandFingerprint, correlationId, command.expectedReservationRevision(), command.amount(),
                command.currency(), command.paymentMethod().name(), command.providerReference(), Timestamp.from(now), Timestamp.from(now), command.quoteId(), command.basis());
        return load(operationId, false).orElseThrow();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public ScheduledPaymentOperation recordLease(UUID operationId, ScheduledPaymentLease lease, Instant now) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        var operation = load(operationId, true).orElseThrow(() -> new ScheduledPaymentOperationNotFoundException(operationId));
        if (operation.status() != PaymentOperationStatus.PREPARING) return operation;
        if (operation.hasLease()) {
            if (operation.fenceToken() == lease.fenceToken()
                    && operation.leaseReservationRevision() == lease.reservationRevision()
                    && operation.lockedAt().equals(lease.lockedAt())
                    && operation.reconcileAfter().equals(lease.reconcileAfter())) return operation;
            throw new DuplicateResourceException("BILLING_PAYMENT_LEASE_CONFLICT", "Clinical returned a changed lease receipt");
        }
        int updated = jdbc.update("""
                UPDATE scheduled_payment_operation
                   SET fence_token=?,lease_reservation_revision=?,locked_at=?,reconcile_after=?,updated_at=?
                 WHERE payment_operation_id=? AND status='PREPARING' AND fence_token IS NULL
                """, lease.fenceToken(), lease.reservationRevision(), Timestamp.from(lease.lockedAt()),
                Timestamp.from(lease.reconcileAfter()), Timestamp.from(now), operationId);
        if (updated != 1) return load(operationId, true).orElseThrow();
        return load(operationId, false).orElseThrow();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 8)
    public ScheduledPaymentOperation complete(UUID operationId, String correlationId, Instant now) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        var operation = load(operationId, true).orElseThrow(() -> new ScheduledPaymentOperationNotFoundException(operationId));
        if (operation.status() != PaymentOperationStatus.PREPARING) return operation;
        require(operation.hasLease(), "BILLING_PAYMENT_LOCK_REQUIRED");
        Source source = jdbc.query("""
                SELECT registration_id,reservation_id,patient_id,source_type,source_id,care_episode_id,
                       registration_status,reservation_status,payment_request_id
                  FROM outpatient_registration_source WHERE registration_id=? FOR UPDATE
                """, SOURCE_MAPPER, operation.registrationId()).stream().findFirst()
                .orElseThrow(() -> new LedgerPaymentRequestNotFoundException(operation.paymentRequestId()));
        if (!operation.paymentRequestId().equals(source.paymentRequestId())
                || !operation.reservationId().equals(source.reservationId())
                || !operation.patientId().equals(source.patientId())
                || !operation.sourceType().equals(source.sourceType()) || !operation.sourceId().equals(source.sourceId())
                || !operation.careEpisodeId().equals(source.careEpisodeId())) {
            throw new BillingRuleException("BILLING_PAYMENT_SOURCE_CONFLICT", "Scheduled payment source binding changed");
        }
        // The persisted lease is the only authority to bypass the old expiry check. Do not re-check
        // the hold TTL here; Clinical may still reconcile a late completion after the session.
        var receipt = ledgerPayments.completeScheduled(operation, now);
        Instant completedAt = receipt.completedAt();
        int updated = jdbc.update("""
                UPDATE scheduled_payment_operation
                   SET status='COMPLETED',transaction_id=?,clearance_id=?,completed_at=?,updated_at=?
                 WHERE payment_operation_id=? AND status='PREPARING' AND fence_token IS NOT NULL
                """, receipt.payment() == null ? null : receipt.payment().transactionId(), receipt.clearanceId(), Timestamp.from(completedAt),
                Timestamp.from(now), operationId);
        if (updated != 1) throw new BillingRuleException("BILLING_PAYMENT_OPERATION_STATE_CONFLICT",
                "Scheduled payment operation changed during commit");
        return load(operationId, false).orElseThrow();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public ScheduledPaymentResult closeAcquireRejected(UUID operationId, String reason, Instant now) {
        if (!List.of("RESERVATION_NOT_PAYABLE", "REVISION_CONFLICT", "PAYMENT_OPERATION_CLOSED",
                "IDEMPOTENCY_CONFLICT").contains(reason)) {
            throw new BillingRuleException("BILLING_PAYMENT_REFUSAL_INVALID", "Clinical refusal is not definitive");
        }
        var operation = load(operationId, true).orElseThrow(() -> new ScheduledPaymentOperationNotFoundException(operationId));
        if (operation.status() != PaymentOperationStatus.PREPARING || operation.hasLease()) return result(operation);
        int updated = jdbc.update("""
                UPDATE scheduled_payment_operation SET status='CLOSED_NOT_PAID',close_reason=?,updated_at=?
                 WHERE payment_operation_id=? AND status='PREPARING' AND fence_token IS NULL
                """, reason, Timestamp.from(now), operationId);
        return result(updated == 1 ? load(operationId, false).orElseThrow() : load(operationId, false).orElseThrow());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public ScheduledPaymentResult closeIfUnpaid(UUID operationId, ClosePaymentCommand command, Instant now) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        var operation = load(operationId, true).orElseThrow(() -> new ScheduledPaymentOperationNotFoundException(operationId));
        validateCloseBinding(operation, command);
        if (operation.status() == PaymentOperationStatus.COMPLETED
                || operation.status() == PaymentOperationStatus.CLOSED_NOT_PAID) return result(operation);
        int updated = jdbc.update("""
                UPDATE scheduled_payment_operation SET status='CLOSED_NOT_PAID',close_reason=?,updated_at=?
                 WHERE payment_operation_id=? AND status='PREPARING'
                """, command.reason(), Timestamp.from(now), operationId);
        return result(updated == 1 ? load(operationId, false).orElseThrow() : load(operationId, false).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true, timeout = 3)
    public ScheduledPaymentResult currentResult(UUID operationId) {
        return result(load(operationId, false).orElseThrow(() -> new ScheduledPaymentOperationNotFoundException(operationId)));
    }

    @Override
    @Transactional(readOnly = true, timeout = 3)
    public ScheduledPaymentOperation findOperation(UUID operationId) {
        return load(operationId, false).orElseThrow(() -> new ScheduledPaymentOperationNotFoundException(operationId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public List<ScheduledPaymentOperation> recoverable(int limit) {
        int bounded = Math.max(1, Math.min(limit, 50));
        return jdbc.query("""
                WITH candidates AS MATERIALIZED (
                    SELECT payment_operation_id, next_recovery_attempt_at, created_at
                      FROM scheduled_payment_operation
                     WHERE (status='PREPARING' OR (status='COMPLETED' AND resolution_applied_at IS NULL))
                       AND next_recovery_attempt_at <= CURRENT_TIMESTAMP
                     ORDER BY next_recovery_attempt_at, created_at, payment_operation_id
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                ), claimed AS (
                    UPDATE scheduled_payment_operation operation
                       SET recovery_attempt_count = operation.recovery_attempt_count + 1,
                           next_recovery_attempt_at = CURRENT_TIMESTAMP + make_interval(secs =>
                               LEAST(900.0, 60.0 * power(2.0, LEAST(operation.recovery_attempt_count, 4))))
                      FROM candidates
                     WHERE operation.payment_operation_id = candidates.payment_operation_id
                    RETURNING operation.*
                )
                SELECT claimed.*
                  FROM claimed
                  JOIN candidates USING (payment_operation_id)
                 ORDER BY candidates.next_recovery_attempt_at, candidates.created_at,
                          candidates.payment_operation_id
                """, OPERATION_MAPPER, bounded);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 3)
    public void markResolutionApplied(UUID operationId, Instant now) {
        int updated = jdbc.update("""
                UPDATE scheduled_payment_operation SET resolution_applied_at=COALESCE(resolution_applied_at,?),updated_at=?
                 WHERE payment_operation_id=? AND status='COMPLETED'
                """, Timestamp.from(now), Timestamp.from(now), operationId);
        if (updated == 0 && load(operationId, false).isEmpty()) throw new ScheduledPaymentOperationNotFoundException(operationId);
    }

    private Optional<ScheduledPaymentOperation> findByIdempotencyKey(String key, boolean forUpdate) {
        return jdbc.query("SELECT * FROM scheduled_payment_operation WHERE idempotency_key=?" + (forUpdate ? " FOR UPDATE" : ""),
                OPERATION_MAPPER, key).stream().findFirst();
    }

    private Optional<ScheduledPaymentOperation> load(UUID operationId, boolean forUpdate) {
        return jdbc.query(SELECT_OPERATION + (forUpdate ? " FOR UPDATE" : ""), OPERATION_MAPPER, operationId).stream().findFirst();
    }

    private static ScheduledPaymentResult result(ScheduledPaymentOperation operation) {
        return switch (operation.status()) {
            case PREPARING -> ScheduledPaymentResult.preparing(operation);
            case COMPLETED -> ScheduledPaymentResult.completed(operation);
            case CLOSED_NOT_PAID -> ScheduledPaymentResult.closed(operation);
        };
    }

    private static void validateCloseBinding(ScheduledPaymentOperation operation, ClosePaymentCommand command) {
        if (command == null || !operation.registrationId().equals(command.registrationId())
                || !operation.reservationId().equals(command.reservationId())
                || command.fenceToken() <= 0
                || operation.fenceToken() != null && operation.fenceToken() != command.fenceToken()
                || !"RESERVATION_RECONCILIATION".equals(command.reason())) {
            throw new DuplicateResourceException("BILLING_PAYMENT_BINDING_CONFLICT", "Close command does not match the stored lease binding");
        }
    }

    private record Source(UUID registrationId, UUID reservationId, UUID patientId, String sourceType,
            UUID sourceId, UUID careEpisodeId, String registrationStatus, String reservationStatus,
            UUID paymentRequestId) { }

    private static final RowMapper<Source> SOURCE_MAPPER = (rs, row) -> new Source(uuid(rs, "registration_id"),
            uuid(rs, "reservation_id"), uuid(rs, "patient_id"), rs.getString("source_type"),
            uuid(rs, "source_id"), uuid(rs, "care_episode_id"), rs.getString("registration_status"),
            rs.getString("reservation_status"), uuid(rs, "payment_request_id"));

    private static final RowMapper<ScheduledPaymentOperation> OPERATION_MAPPER = (rs, row) -> new ScheduledPaymentOperation(
            uuid(rs, "payment_operation_id"), uuid(rs, "payment_request_id"), uuid(rs, "account_id"),
            uuid(rs, "registration_id"), uuid(rs, "reservation_id"), uuid(rs, "patient_id"),
            rs.getString("source_type"), uuid(rs, "source_id"), uuid(rs, "care_episode_id"),
            uuid(rs, "actor_account_id"), rs.getString("idempotency_key"), rs.getString("command_fingerprint"),
            rs.getString("correlation_id"), rs.getLong("expected_reservation_revision"), rs.getBigDecimal("amount"),
            rs.getString("currency").trim(), com.mediflow.billing.domain.model.PaymentMethod.valueOf(rs.getString("payment_method")),
            rs.getString("provider_reference"), PaymentOperationStatus.valueOf(rs.getString("status")),
            uuid(rs, "transaction_id"), uuid(rs, "clearance_id"), nullableLong(rs, "fence_token"),
            nullableLong(rs, "lease_reservation_revision"), instant(rs, "locked_at"), instant(rs, "reconcile_after"),
            instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "completed_at"), instant(rs, "resolution_applied_at"), uuid(rs, "quote_id"), rs.getString("basis"));

    private static UUID uuid(ResultSet rs, String name) throws SQLException { return rs.getObject(name, UUID.class); }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }
    private static Long nullableLong(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }
    private static void require(boolean valid, String code) {
        if (!valid) throw new BillingRuleException(code, code);
    }
}
