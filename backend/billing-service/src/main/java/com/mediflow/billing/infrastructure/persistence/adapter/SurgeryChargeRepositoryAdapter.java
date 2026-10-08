package com.mediflow.billing.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.billing.application.port.out.SurgeryChargeRepositoryPort;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.domain.model.Charge;
import com.mediflow.billing.domain.model.ChargeStatus;
import com.mediflow.billing.domain.model.PaymentTransaction;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class SurgeryChargeRepositoryAdapter implements SurgeryChargeRepositoryPort {

    private final JdbcTemplate jdbc;

    public SurgeryChargeRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BillingAccount findOrOpenAccount(UUID patientId, UUID departmentId, CareEpisodeType careEpisodeType,
                                             UUID careEpisodeId, String currency, Instant openedAt) {
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id, patient_id, department_id, care_episode_type,
                    care_episode_id, status, currency, opened_at)
                VALUES (?,?,?,?,?,'OPEN',?,?)
                ON CONFLICT (care_episode_type, care_episode_id) DO NOTHING
                """, UUID.randomUUID(), patientId, departmentId, careEpisodeType.name(), careEpisodeId,
                currency, at(openedAt));
        return jdbc.query("""
                SELECT * FROM BILLING_ACCOUNT WHERE care_episode_type = ? AND care_episode_id = ? FOR UPDATE
                """, (rs, row) -> account(rs), careEpisodeType.name(), careEpisodeId).getFirst();
    }

    @Override
    public Optional<BillingAccount> findAccountById(UUID accountId) {
        return jdbc.query("SELECT * FROM BILLING_ACCOUNT WHERE account_id = ?", (rs, row) -> account(rs), accountId)
                .stream().findFirst();
    }

    @Override
    public Optional<Charge> findChargeBySource(String sourceType, UUID sourceId, String priceCode) {
        return jdbc.query("SELECT * FROM CHARGE WHERE source_type = ? AND source_id = ? AND price_code = ?",
                (rs, row) -> charge(rs), sourceType, sourceId, priceCode).stream().findFirst();
    }

    @Override
    public List<Charge> findChargesBySource(String sourceType, UUID sourceId) {
        return jdbc.query("SELECT * FROM CHARGE WHERE source_type = ? AND source_id = ?",
                (rs, row) -> charge(rs), sourceType, sourceId);
    }

    @Override
    public Charge saveCharge(Charge charge) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO CHARGE(charge_id, account_id, patient_id, department_id, source_type, source_id,
                    price_code, description, quantity, unit_amount, gross_amount, status, void_reason,
                    reconciled_result_id, incurred_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, charge.getAccountId(), charge.getPatientId(), charge.getDepartmentId(), charge.getSourceType(),
                charge.getSourceId(), charge.getPriceCode(), charge.getDescription(), charge.getQuantity(),
                charge.getUnitAmount(), charge.getGrossAmount(), charge.getStatus().name(), charge.getVoidReason(),
                charge.getReconciledResultId(), at(charge.getIncurredAt()));
        return Charge.restore(id, charge.getAccountId(), charge.getPatientId(), charge.getDepartmentId(),
                charge.getSourceType(), charge.getSourceId(), charge.getPriceCode(), charge.getDescription(),
                charge.getQuantity(), charge.getUnitAmount(), charge.getGrossAmount(), charge.getStatus(),
                charge.getVoidReason(), charge.getReconciledResultId(), charge.getIncurredAt(), null);
    }

    @Override
    public void updateCharge(Charge charge) {
        jdbc.update("""
                UPDATE CHARGE SET quantity = ?, unit_amount = ?, gross_amount = ?, status = ?, void_reason = ?,
                    reconciled_result_id = ?
                 WHERE charge_id = ?
                """, charge.getQuantity(), charge.getUnitAmount(), charge.getGrossAmount(), charge.getStatus().name(),
                charge.getVoidReason(), charge.getReconciledResultId(), charge.getChargeId());
    }

    @Override
    public List<ChargeAllocation> findCompletedAllocations(UUID chargeId) {
        return jdbc.query("""
                SELECT a.transaction_id, a.amount, t.payment_request_id, t.currency, t.payment_method
                  FROM PAYMENT_ALLOCATION a JOIN PAYMENT_TRANSACTION t ON t.transaction_id = a.transaction_id
                 WHERE a.charge_id = ? AND t.transaction_type = 'PAYMENT' AND t.status = 'COMPLETED'
                """, (rs, row) -> new ChargeAllocation(uuid(rs, "transaction_id"), rs.getBigDecimal("amount"),
                        uuid(rs, "payment_request_id"), rs.getString("currency").trim(), rs.getString("payment_method")),
                chargeId);
    }

    @Override
    public boolean refundTransactionExists(String idempotencyKey) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM PAYMENT_TRANSACTION WHERE idempotency_key = ?",
                Integer.class, idempotencyKey);
        return count != null && count > 0;
    }

    @Override
    public void saveRefund(PaymentTransaction refund, UUID chargeId) {
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id, account_id, payment_request_id, transaction_type,
                    classification, status, amount, currency, payment_method, provider_reference, idempotency_key,
                    original_transaction_id, completed_at, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, refund.getTransactionId(), refund.getAccountId(), refund.getPaymentRequestId(),
                refund.getTransactionType().name(), refund.getClassification().name(), refund.getStatus().name(),
                refund.getAmount(), refund.getCurrency(), refund.getPaymentMethod(), refund.getProviderReference(),
                refund.getIdempotencyKey(), refund.getOriginalTransactionId(), at(refund.getCompletedAt()),
                at(refund.getCreatedAt()));
        jdbc.update("INSERT INTO PAYMENT_ALLOCATION(allocation_id, transaction_id, charge_id, amount) VALUES (?,?,?,?)",
                UUID.randomUUID(), refund.getTransactionId(), chargeId, refund.getAmount());
    }

    private BillingAccount account(ResultSet rs) throws SQLException {
        return BillingAccount.restore(uuid(rs, "account_id"), uuid(rs, "patient_id"), uuid(rs, "department_id"),
                CareEpisodeType.valueOf(rs.getString("care_episode_type")), uuid(rs, "care_episode_id"),
                AccountStatus.valueOf(rs.getString("status")), rs.getString("currency").trim(), rs.getLong("version"),
                instant(rs, "opened_at"), instant(rs, "charge_closed_at"), instant(rs, "closed_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private Charge charge(ResultSet rs) throws SQLException {
        return Charge.restore(uuid(rs, "charge_id"), uuid(rs, "account_id"), uuid(rs, "patient_id"),
                uuid(rs, "department_id"), rs.getString("source_type"), uuid(rs, "source_id"), rs.getString("price_code"),
                rs.getString("description"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_amount"),
                rs.getBigDecimal("gross_amount"), ChargeStatus.valueOf(rs.getString("status")), rs.getString("void_reason"),
                uuid(rs, "reconciled_result_id"), instant(rs, "incurred_at"), instant(rs, "created_at"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException { return rs.getObject(column, UUID.class); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private static Timestamp at(Instant value) { return value == null ? null : Timestamp.from(value); }
}
