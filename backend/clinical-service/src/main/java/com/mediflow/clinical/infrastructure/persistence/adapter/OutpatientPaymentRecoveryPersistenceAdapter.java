package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.authority.PaymentRecoveryOperation;
import com.mediflow.clinical.application.port.out.OutpatientPaymentRecoveryPort;

/** PostgreSQL durable claims for payment recovery; network calls are never made under this transaction. */
@Repository
public class OutpatientPaymentRecoveryPersistenceAdapter implements OutpatientPaymentRecoveryPort {
    private static final RowMapper<PaymentRecoveryOperation> CLAIM_MAPPER = (row, index) ->
            new PaymentRecoveryOperation(row.getObject("payment_operation_id", UUID.class),
                    row.getObject("payment_request_id", UUID.class), row.getObject("registration_id", UUID.class),
                    row.getObject("reservation_id", UUID.class), row.getObject("patient_id", UUID.class),
                    row.getString("source_type"), row.getObject("source_id", UUID.class),
                    row.getObject("care_episode_id", UUID.class), nullableLong(row, "fence_token"),
                    row.getString("basis"), row.getObject("quote_id", UUID.class),
                    instant(row, "reconcile_after"), instant(row, "recovery_first_attempt_at"),
                    row.getInt("recovery_attempt_count"), row.getObject("recovery_owner", UUID.class));

    private final NamedParameterJdbcTemplate jdbc;

    public OutpatientPaymentRecoveryPersistenceAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public List<PaymentRecoveryOperation> claimDue(UUID owner, Instant now, Instant leaseUntil, int limit) {
        if (owner == null || now == null || leaseUntil == null || !leaseUntil.isAfter(now)
                || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Payment recovery claim bounds are invalid");
        }
        var parameters = new MapSqlParameterSource()
                .addValue("owner", owner)
                .addValue("now", Timestamp.from(now))
                .addValue("leaseUntil", Timestamp.from(leaseUntil))
                .addValue("limit", limit);
        return jdbc.query("""
                WITH due AS (
                    SELECT payment_operation_id
                    FROM outpatient_payment_operation_receipt
                    WHERE state = 'PAYMENT_LOCKED'
                      AND reconcile_after <= :now
                      AND (recovery_next_attempt_at IS NULL OR recovery_next_attempt_at <= :now)
                      AND (recovery_lease_until IS NULL OR recovery_lease_until <= :now)
                    ORDER BY reconcile_after, payment_operation_id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE outpatient_payment_operation_receipt receipt
                SET recovery_owner = :owner,
                    recovery_lease_until = :leaseUntil,
                    recovery_first_attempt_at = COALESCE(receipt.recovery_first_attempt_at, :now),
                    recovery_last_attempt_at = :now,
                    recovery_attempt_count = receipt.recovery_attempt_count + 1
                FROM due
                WHERE receipt.payment_operation_id = due.payment_operation_id
                RETURNING receipt.payment_operation_id, receipt.payment_request_id, receipt.registration_id,
                    receipt.reservation_id, receipt.patient_id, receipt.source_type, receipt.source_id,
                    receipt.care_episode_id, receipt.fence_token, receipt.basis, receipt.quote_id,
                    receipt.reconcile_after, receipt.recovery_first_attempt_at, receipt.recovery_attempt_count,
                    receipt.recovery_owner
                """, parameters, CLAIM_MAPPER);
    }

    @Override
    @Transactional
    public void complete(PaymentRecoveryOperation operation) {
        jdbc.update("""
                UPDATE outpatient_payment_operation_receipt
                SET recovery_owner = NULL, recovery_lease_until = NULL, recovery_next_attempt_at = NULL,
                    recovery_last_error_code = NULL
                WHERE payment_operation_id = :operationId AND recovery_owner = :owner
                """, new MapSqlParameterSource().addValue("operationId", operation.paymentOperationId())
                .addValue("owner", operation.recoveryOwner()));
    }

    @Override
    @Transactional
    public boolean defer(PaymentRecoveryOperation operation, Instant now, Instant retryAt, String errorCode) {
        if (operation == null || operation.paymentOperationId() == null || operation.recoveryOwner() == null
                || now == null || retryAt == null || !retryAt.isAfter(now)
                || errorCode == null || errorCode.isBlank() || errorCode.length() > 120) {
            throw new IllegalArgumentException("Payment recovery retry data is invalid");
        }
        var parameters = new MapSqlParameterSource().addValue("operationId", operation.paymentOperationId())
                .addValue("owner", operation.recoveryOwner());
        List<RecoveryAlertState> states = jdbc.query("""
                SELECT recovery_first_attempt_at, recovery_alerted_at
                FROM outpatient_payment_operation_receipt
                WHERE payment_operation_id = :operationId AND recovery_owner = :owner
                  AND state = 'PAYMENT_LOCKED'
                FOR UPDATE
                """, parameters, (row, index) -> new RecoveryAlertState(
                instant(row, "recovery_first_attempt_at"), instant(row, "recovery_alerted_at")));
        if (states.isEmpty()) return false;
        RecoveryAlertState state = states.getFirst();
        boolean alert = state.alertedAt() == null && state.firstAttemptAt() != null
                && !now.isBefore(state.firstAttemptAt().plusSeconds(300));
        parameters.addValue("now", Timestamp.from(now))
                .addValue("retryAt", Timestamp.from(retryAt))
                .addValue("errorCode", errorCode)
                .addValue("alertAt", alert ? Timestamp.from(now) : null)
                .addValue("shouldAlert", alert);
        jdbc.update("""
                UPDATE outpatient_payment_operation_receipt
                SET recovery_owner = NULL, recovery_lease_until = NULL,
                    recovery_next_attempt_at = :retryAt, recovery_last_error_code = :errorCode,
                    recovery_alerted_at = CASE WHEN :shouldAlert THEN :alertAt ELSE recovery_alerted_at END
                WHERE payment_operation_id = :operationId AND recovery_owner = :owner
                  AND state = 'PAYMENT_LOCKED'
                """, parameters);
        return alert;
    }

    private static Long nullableLong(ResultSet row, String name) throws SQLException {
        Object value = row.getObject(name);
        return value == null ? null : ((Number) value).longValue();
    }

    private static Instant instant(ResultSet row, String name) throws SQLException {
        Timestamp value = row.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    private record RecoveryAlertState(Instant firstAttemptAt, Instant alertedAt) { }
}
