package com.mediflow.billing.infrastructure.persistence.adapter;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.command.ReconcileAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.SettlementReconciledPayload;
import com.mediflow.billing.application.port.out.AdmissionReconciliationStorePort;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.MANDATORY)
public class AdmissionReconciliationStoreAdapter implements AdmissionReconciliationStorePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public AdmissionReconciliationStoreAdapter(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    @Override public Optional<SettlementReconciledPayload> lockAndFindReplay(ReconcileAdmissionCommand command) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Admission reconciliation requires an active transaction");
        }
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                Object.class, "admission-reconciliation-key:" + command.idempotencyKey());
        var rows = jdbc.query("SELECT * FROM admission_reconciliation WHERE idempotency_key=?", (rs, row) -> {
            if (!command.accountId().equals(rs.getObject("account_id", UUID.class))
                    || !command.actorId().equals(rs.getObject("actor_id", UUID.class))) {
                throw new DuplicateResourceException("BILLING_RECONCILIATION_KEY_CONFLICT", "Idempotency key already belongs to another operation");
            }
            return decode(rs.getString("payload"));
        }, command.idempotencyKey());
        return rows.stream().findFirst();
    }
    @Override public Revision lockAndFindRevision(UUID accountId) {
        if (jdbc.query("SELECT account_id FROM billing_account WHERE account_id=? FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), accountId).isEmpty()) {
            throw new ResourceNotFoundException("BILLING_ACCOUNT_NOT_FOUND", "Billing account not found");
        }
        // Establish a write fence: concurrent repeatable-read callers must retry, not read an old revision set.
        jdbc.update("UPDATE billing_account SET version=version+1 WHERE account_id=?", accountId);
        var rows = jdbc.query("""
                SELECT settlement_id,settlement_version,reconciled_at FROM admission_reconciliation
                WHERE account_id=? ORDER BY settlement_version DESC LIMIT 1
                """, (rs, row) -> new Revision(Math.addExact(rs.getInt("settlement_version"), 1),
                    rs.getObject("settlement_id", UUID.class), rs.getTimestamp("reconciled_at").toInstant()), accountId);
        return rows.isEmpty() ? new Revision(1, null, null) : rows.getFirst();
    }
    @Override public void save(ReconcileAdmissionCommand command, SettlementReconciledPayload payload) {
        try {
            jdbc.update("""
                    INSERT INTO admission_reconciliation(settlement_id,account_id,settlement_version,supersedes_settlement_id,
                        actor_id,idempotency_key,reconciled_at,payload) VALUES (?,?,?,?,?,?,?,?)
                    """, payload.settlementId(), command.accountId(), payload.settlementVersion(), payload.supersedesSettlementId(),
                        command.actorId(), command.idempotencyKey(), Timestamp.from(payload.reconciledAt()), mapper.writeValueAsString(payload));
        } catch (JsonProcessingException error) { throw new IllegalStateException("Cannot encode reconciliation snapshot", error); }
    }
    private SettlementReconciledPayload decode(String payload) {
        try { return mapper.readValue(payload, SettlementReconciledPayload.class); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Cannot read reconciliation snapshot", error); }
    }
}
