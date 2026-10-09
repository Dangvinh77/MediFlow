package com.mediflow.billing.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.mediflow.billing.application.port.out.LedgerRefundRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.*;
import com.mediflow.common.exception.ResourceNotFoundException;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class LedgerRefundRepositoryAdapter implements LedgerRefundRepositoryPort {
    private final JdbcTemplate jdbc;
    public LedgerRefundRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public RefundContext lockOriginal(UUID originalId) {
        var accountIds = jdbc.queryForList("SELECT account_id FROM PAYMENT_TRANSACTION WHERE transaction_id=?", UUID.class, originalId);
        if (accountIds.isEmpty()) throw new ResourceNotFoundException("BILLING_TRANSACTION_NOT_FOUND", "Payment transaction not found");
        var account = jdbc.query("SELECT * FROM BILLING_ACCOUNT WHERE account_id=? FOR UPDATE", (rs, row) ->
                BillingAccount.restore(id(rs, "account_id"), id(rs, "patient_id"), id(rs, "department_id"),
                        CareEpisodeType.valueOf(rs.getString("care_episode_type")), id(rs, "care_episode_id"),
                        AccountStatus.valueOf(rs.getString("status")), rs.getString("currency").trim(), rs.getLong("version"),
                        time(rs, "opened_at"), time(rs, "charge_closed_at"), time(rs, "closed_at"),
                        time(rs, "created_at"), time(rs, "updated_at")), accountIds.getFirst()).getFirst();
        var original = jdbc.query("SELECT * FROM PAYMENT_TRANSACTION WHERE transaction_id=? FOR UPDATE", (rs, row) ->
                PaymentTransaction.restore(id(rs, "transaction_id"), id(rs, "account_id"), id(rs, "payment_request_id"),
                        PaymentTransactionType.valueOf(rs.getString("transaction_type")),
                        PaymentClassification.valueOf(rs.getString("classification")),
                        PaymentTransactionStatus.valueOf(rs.getString("status")), rs.getBigDecimal("amount"),
                        rs.getString("currency").trim(), rs.getString("payment_method"), rs.getString("provider_reference"),
                        rs.getString("idempotency_key"), id(rs, "original_transaction_id"),
                        time(rs, "completed_at"), time(rs, "created_at")), originalId).getFirst();
        var refunded = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM PAYMENT_TRANSACTION
                WHERE original_transaction_id=? AND transaction_type IN ('REFUND','REVERSAL') AND status='COMPLETED'
                """, BigDecimal.class, originalId);
        return new RefundContext(account, original, refunded);
    }

    @Override
    public Optional<String> refundReason(UUID refundId) {
        return jdbc.queryForList("SELECT reason FROM ledger_refund_evidence WHERE refund_transaction_id=?", String.class, refundId)
                .stream().findFirst();
    }

    @Override
    public void append(PaymentTransaction refund, String reason, UUID actor) {
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,payment_request_id,transaction_type,
                    classification,status,amount,currency,payment_method,provider_reference,idempotency_key,
                    original_transaction_id,completed_at,created_at,actor_account_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, refund.getTransactionId(), refund.getAccountId(), refund.getPaymentRequestId(),
                refund.getTransactionType().name(), refund.getClassification().name(), refund.getStatus().name(),
                refund.getAmount(), refund.getCurrency(), refund.getPaymentMethod(), null, refund.getIdempotencyKey(),
                refund.getOriginalTransactionId(), Timestamp.from(refund.getCompletedAt()), Timestamp.from(refund.getCreatedAt()), actor);
        jdbc.update("""
                INSERT INTO ledger_refund_evidence(refund_transaction_id,original_transaction_id,reason,created_at)
                VALUES (?,?,?,?)
                """, refund.getTransactionId(), refund.getOriginalTransactionId(), reason, Timestamp.from(refund.getCompletedAt()));
    }

    @Override
    public void reverseAllocations(PaymentTransaction original, PaymentTransaction refund) {
        var budgets = jdbc.query("""
                SELECT a.charge_id,c.account_id,c.patient_id,
                       a.amount - COALESCE((SELECT SUM(r.amount) FROM PAYMENT_ALLOCATION r
                         JOIN PAYMENT_TRANSACTION t ON t.transaction_id=r.transaction_id
                         WHERE r.charge_id=a.charge_id AND t.original_transaction_id=a.transaction_id
                           AND t.status='COMPLETED' AND t.transaction_type IN ('REFUND','REVERSAL')),0) AS available
                FROM PAYMENT_ALLOCATION a JOIN CHARGE c ON c.charge_id=a.charge_id
                WHERE a.transaction_id=? ORDER BY a.charge_id
                """, (rs, row) -> new Budget(id(rs, "charge_id"), id(rs, "account_id"), id(rs, "patient_id"),
                        rs.getBigDecimal("available")), original.getTransactionId());
        if (original.getClassification() == PaymentClassification.ADMISSION_DEPOSIT) {
            if (!budgets.isEmpty()) throw rule("BILLING_DEPOSIT_REFUND_ALLOCATION_UNSUPPORTED");
            return;
        }
        // Reverse only this original payment's actual lines, never another payment's allocations.
        var remaining = refund.getAmount();
        var patient = jdbc.queryForObject("SELECT patient_id FROM BILLING_ACCOUNT WHERE account_id=?", UUID.class, original.getAccountId());
        for (var budget : budgets) {
            if (!original.getAccountId().equals(budget.accountId()) || !patient.equals(budget.patientId()) || budget.available().signum() < 0)
                throw rule("BILLING_REFUND_ALLOCATION_MISMATCH");
            var amount = remaining.min(budget.available());
            if (amount.signum() > 0) {
                jdbc.update("INSERT INTO PAYMENT_ALLOCATION(allocation_id,transaction_id,charge_id,amount) VALUES (?,?,?,?)",
                        UUID.randomUUID(), refund.getTransactionId(), budget.chargeId(), amount);
                remaining = remaining.subtract(amount);
            }
        }
        if (remaining.signum() != 0) throw rule("BILLING_REFUND_ALLOCATION_EXCEEDS_ORIGINAL");
    }

    @Override
    public void revokeUnsatisfiedClearance(UUID requestId, Instant revokedAt) {
        jdbc.update("""
                UPDATE FINANCIAL_CLEARANCE g SET revoked_at=?
                WHERE g.payment_request_id=? AND g.revoked_at IS NULL
                  AND (SELECT COALESCE(SUM(CASE WHEN t.transaction_type='PAYMENT' THEN t.amount ELSE -t.amount END),0)
                       FROM PAYMENT_TRANSACTION t WHERE t.payment_request_id=g.payment_request_id AND t.status='COMPLETED') < g.amount
                """, Timestamp.from(revokedAt), requestId);
    }

    private record Budget(UUID chargeId, UUID accountId, UUID patientId, BigDecimal available) { }
    private static UUID id(ResultSet rs, String column) throws SQLException { return rs.getObject(column, UUID.class); }
    private static Instant time(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private static BillingRuleException rule(String code) { return new BillingRuleException(code, code); }
}
