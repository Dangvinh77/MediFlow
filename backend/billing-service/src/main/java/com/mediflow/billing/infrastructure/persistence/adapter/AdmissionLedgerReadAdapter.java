package com.mediflow.billing.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.AdmissionLedgerSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Billing-owned rows only. A bounded, read-only repeatable snapshot; no lock or monetary mutation. */
@Repository
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.settlement-preview.enabled"}, havingValue = "true")
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class AdmissionLedgerReadAdapter implements AdmissionLedgerReadPort {
    static final int MAX_ROWS_PER_TABLE = 10_000;
    private static final int QUERY_TIMEOUT_SECONDS = 3;
    private static final int FETCH_SIZE = 128;
    private final JdbcTemplate jdbc;
    public AdmissionLedgerReadAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<AdmissionLedgerSnapshot> read(UUID accountId) {
        String isolation = jdbc.queryForObject("SHOW transaction_isolation", String.class);
        if (!("repeatable read".equals(isolation) || "serializable".equals(isolation)))
            throw new IllegalStateException("Admission ledger preview requires a repeatable database snapshot");
        var accounts = query("""
                SELECT *, statement_timestamp() AS observed_at FROM BILLING_ACCOUNT WHERE account_id=?
                """, (rs, row) -> {
            if (!"ADMISSION".equals(rs.getString("care_episode_type"))) throw new BillingRuleException(
                    "BILLING_EPISODE_MISMATCH", "Settlement preview requires an admission account");
            var closed = rs.getTimestamp("charge_closed_at");
            return new AdmissionLedgerSnapshot(rs.getObject("account_id", UUID.class), rs.getObject("care_episode_id", UUID.class),
                    rs.getObject("patient_id", UUID.class), rs.getObject("department_id", UUID.class), rs.getString("currency").trim(),
                    AccountStatus.valueOf(rs.getString("status")), closed == null ? null : closed.toInstant(),
                    rs.getTimestamp("observed_at").toInstant(), List.of(), List.of(), List.of(), List.of());
        }, accountId);
        if (accounts.isEmpty()) return Optional.empty();
        var account = accounts.getFirst();
        var charges = bounded("""
                SELECT charge_id,account_id,patient_id,status,gross_amount FROM CHARGE WHERE account_id=? LIMIT %d
                """.formatted(MAX_ROWS_PER_TABLE + 1), (rs, row) -> new AdmissionLedgerSnapshot.ChargeLine(rs.getObject("charge_id", UUID.class),
                rs.getObject("account_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("status"), rs.getBigDecimal("gross_amount")), accountId);
        var transactions = bounded("""
                SELECT transaction_id,account_id,original_transaction_id,transaction_type,classification,status,amount,currency,completed_at
                FROM PAYMENT_TRANSACTION WHERE account_id=? LIMIT %d
                """.formatted(MAX_ROWS_PER_TABLE + 1), (rs, row) -> {
            var completed = rs.getTimestamp("completed_at");
            return new AdmissionLedgerSnapshot.TransactionLine(rs.getObject("transaction_id", UUID.class), rs.getObject("account_id", UUID.class),
                    rs.getObject("original_transaction_id", UUID.class), rs.getString("transaction_type"), rs.getString("classification"),
                    rs.getString("status"), rs.getBigDecimal("amount"), rs.getString("currency").trim(), completed == null ? null : completed.toInstant());
        }, accountId);
        // Include inconsistent cross-account relationships rather than filtering them into plausible totals.
        var allocations = bounded("""
                SELECT a.transaction_id,a.charge_id,a.amount FROM PAYMENT_ALLOCATION a
                JOIN PAYMENT_TRANSACTION t ON t.transaction_id=a.transaction_id JOIN CHARGE c ON c.charge_id=a.charge_id
                WHERE t.account_id=? OR c.account_id=? LIMIT %d
                """.formatted(MAX_ROWS_PER_TABLE + 1), (rs, row) -> new AdmissionLedgerSnapshot.AllocationLine(rs.getObject("transaction_id", UUID.class),
                rs.getObject("charge_id", UUID.class), rs.getBigDecimal("amount")), accountId, accountId);
        var insurance = bounded("""
                SELECT adjustment_id,account_id,admission_id,original_adjustment_id,adjustment_type,amount
                FROM INSURANCE_ADJUSTMENT WHERE account_id=? LIMIT %d
                """.formatted(MAX_ROWS_PER_TABLE + 1), (rs, row) -> new AdmissionLedgerSnapshot.InsuranceLine(rs.getObject("adjustment_id", UUID.class),
                rs.getObject("account_id", UUID.class), rs.getObject("admission_id", UUID.class), rs.getObject("original_adjustment_id", UUID.class),
                rs.getString("adjustment_type"), rs.getBigDecimal("amount")), accountId);
        return Optional.of(new AdmissionLedgerSnapshot(account.accountId(), account.admissionId(), account.patientId(), account.departmentId(),
                account.currency(), account.accountStatus(), account.chargeClosedAt(), account.observedAt(), charges, transactions, allocations, insurance));
    }

    private <T> List<T> bounded(String sql, RowMapper<T> mapper, Object... parameters) {
        var rows = query(sql, mapper, parameters);
        if (rows.size() > MAX_ROWS_PER_TABLE) throw new BillingRuleException("BILLING_SETTLEMENT_PREVIEW_LIMIT",
                "Admission ledger exceeds the bounded preview; no partial total was returned");
        return rows;
    }

    private <T> List<T> query(String sql, RowMapper<T> mapper, Object... parameters) {
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(sql);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setFetchSize(FETCH_SIZE);
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            return statement;
        }, mapper);
    }
}
