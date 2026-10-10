package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.port.out.AdmissionSettlementRepositoryPort;
import com.mediflow.billing.domain.exception.BillingAccountNotFoundException;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.domain.model.InsuranceAdjustment;
import com.mediflow.billing.domain.model.Settlement;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AdmissionSettlementRepositoryAdapter implements AdmissionSettlementRepositoryPort {
    private final JdbcTemplate jdbc;
    public AdmissionSettlementRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public BillingAccount lockAccountByAdmission(UUID admissionId) {
        var rows = jdbc.query("SELECT * FROM BILLING_ACCOUNT WHERE care_episode_type='ADMISSION' AND care_episode_id=? FOR UPDATE",
                this::toAccount, admissionId);
        if (rows.isEmpty()) throw BillingAccountNotFoundException.byAdmission(admissionId);
        return rows.getFirst();
    }
    @Override public BillingAccount lockAccountById(UUID accountId) {
        var rows = jdbc.query("SELECT * FROM BILLING_ACCOUNT WHERE account_id=? FOR UPDATE", this::toAccount, accountId);
        if (rows.isEmpty()) throw BillingAccountNotFoundException.byAccountId(accountId);
        return rows.getFirst();
    }
    @Override public void updateAccountStatus(BillingAccount account) {
        jdbc.update("UPDATE BILLING_ACCOUNT SET status=?, charge_closed_at=?, closed_at=?, updated_at=now() WHERE account_id=?",
                account.getStatus().name(), at(account.getChargeClosedAt()), at(account.getClosedAt()), account.getAccountId());
    }
    @Override public void recordInsuranceAdjustment(InsuranceAdjustment adjustment) {
        var existing = jdbc.query("SELECT amount FROM INSURANCE_ADJUSTMENT WHERE account_id=? AND decision_reference=? AND adjustment_type=?",
                (rs, row) -> rs.getBigDecimal("amount"), adjustment.getAccountId(), adjustment.getDecisionReference(),
                adjustment.getAdjustmentType().name());
        if (!existing.isEmpty()) {
            if (existing.getFirst().compareTo(adjustment.getAmount()) != 0)
                throw new BillingRuleException("BILLING_INSURANCE_DECISION_CONFLICT", "BILLING_INSURANCE_DECISION_CONFLICT");
            return;
        }
        jdbc.update("""
                INSERT INTO INSURANCE_ADJUSTMENT(adjustment_id,account_id,admission_id,decision_reference,
                    adjustment_type,original_adjustment_id,amount,reason,created_at) VALUES (?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), adjustment.getAccountId(), adjustment.getAdmissionId(), adjustment.getDecisionReference(),
                adjustment.getAdjustmentType().name(), adjustment.getOriginalAdjustmentId(), adjustment.getAmount(),
                adjustment.getReason(), at(adjustment.getCreatedAt()));
    }
    @Override public SettlementContext loadContext(UUID accountId) {
        BigDecimal gross = jdbc.queryForObject(
                "SELECT COALESCE(SUM(gross_amount),0) FROM CHARGE WHERE account_id=? AND status='POSTED'", BigDecimal.class, accountId);
        BigDecimal completedPayments = jdbc.queryForObject("""
                SELECT COALESCE(SUM(a.amount),0) FROM PAYMENT_ALLOCATION a JOIN PAYMENT_TRANSACTION t ON t.transaction_id=a.transaction_id
                 WHERE t.account_id=? AND t.status='COMPLETED' AND t.transaction_type='PAYMENT'
                """, BigDecimal.class, accountId);
        BigDecimal completedRefunds = jdbc.queryForObject("""
                SELECT COALESCE(SUM(a.amount),0) FROM PAYMENT_ALLOCATION a JOIN PAYMENT_TRANSACTION t ON t.transaction_id=a.transaction_id
                 WHERE t.account_id=? AND t.status='COMPLETED' AND t.transaction_type IN ('REFUND','REVERSAL')
                """, BigDecimal.class, accountId);
        BigDecimal cumulativeInsurance = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN adjustment_type='APPROVAL' THEN amount ELSE -amount END),0)
                  FROM INSURANCE_ADJUSTMENT WHERE account_id=?
                """, BigDecimal.class, accountId);
        List<ChargeRemaining> remaining = jdbc.query("""
                SELECT c.charge_id, c.gross_amount - COALESCE((
                    SELECT SUM(CASE WHEN t.transaction_type='PAYMENT' THEN a.amount ELSE -a.amount END)
                      FROM PAYMENT_ALLOCATION a JOIN PAYMENT_TRANSACTION t ON t.transaction_id=a.transaction_id
                     WHERE a.charge_id=c.charge_id AND t.status='COMPLETED'), 0) AS remaining
                  FROM CHARGE c WHERE c.account_id=? AND c.status='POSTED' ORDER BY c.charge_id
                """, (rs, row) -> new ChargeRemaining(uuid(rs, "charge_id"), rs.getBigDecimal("remaining")), accountId);
        remaining = remaining.stream().filter(r -> r.remaining().signum() > 0).toList();
        var latest = jdbc.query("""
                SELECT settlement_id, settlement_version FROM SETTLEMENT WHERE account_id=?
                 ORDER BY settlement_version DESC LIMIT 1
                """, (rs, row) -> new Object[]{uuid(rs, "settlement_id"), rs.getInt("settlement_version")}, accountId);
        int nextVersion = latest.isEmpty() ? 1 : (int) latest.getFirst()[1] + 1;
        UUID previousId = latest.isEmpty() ? null : (UUID) latest.getFirst()[0];
        return new SettlementContext(gross, completedPayments, completedRefunds, cumulativeInsurance, remaining, nextVersion, previousId);
    }
    @Override public UUID createSettlementPaymentRequest(UUID accountId, BigDecimal amount, List<ChargeRemaining> charges, UUID createdBy) {
        UUID request = UUID.randomUUID(), invoice = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency,created_by)
                VALUES (?,?,?,'SETTLEMENT',?,'VND',?)
                """, request, invoice, accountId, amount, createdBy);
        for (var charge : charges) jdbc.update(
                "INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                request, charge.chargeId(), charge.remaining());
        return request;
    }
    @Override public Settlement saveSettlement(Settlement settlement) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO SETTLEMENT(settlement_id,account_id,admission_id,settlement_version,supersedes_settlement_id,
                    gross_amount,insurance_amount,patient_liability,completed_payments,completed_refunds,balance,outcome,completed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, settlement.getAccountId(), settlement.getAdmissionId(), settlement.getSettlementVersion(),
                settlement.getSupersedesSettlementId(), settlement.getGrossAmount(), settlement.getInsuranceAmount(),
                settlement.getPatientLiability(), settlement.getCompletedPayments(), settlement.getCompletedRefunds(),
                settlement.getBalance(), settlement.getOutcome().name(), at(settlement.getCompletedAt()));
        return Settlement.restore(id, settlement.getAccountId(), settlement.getAdmissionId(), settlement.getSettlementVersion(),
                settlement.getSupersedesSettlementId(), settlement.getGrossAmount(), settlement.getInsuranceAmount(),
                settlement.getPatientLiability(), settlement.getCompletedPayments(), settlement.getCompletedRefunds(),
                settlement.getBalance(), settlement.getOutcome(), settlement.getCompletedAt());
    }

    private BillingAccount toAccount(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return BillingAccount.restore(uuid(rs, "account_id"), uuid(rs, "patient_id"), uuid(rs, "department_id"),
                CareEpisodeType.valueOf(rs.getString("care_episode_type")), uuid(rs, "care_episode_id"),
                AccountStatus.valueOf(rs.getString("status")), rs.getString("currency").trim(), rs.getLong("version"),
                instant(rs, "opened_at"), instant(rs, "charge_closed_at"), instant(rs, "closed_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }
    private static UUID uuid(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var value = rs.getObject(column, UUID.class); return value;
    }
    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var ts = rs.getTimestamp(column); return ts == null ? null : ts.toInstant();
    }
    private static Timestamp at(Instant instant) { return instant == null ? null : Timestamp.from(instant); }
}
