package com.mediflow.billing.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.AllocateAdmissionDepositCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.DepositAllocatedPayload;
import com.mediflow.billing.application.port.out.AdmissionDepositAllocationStorePort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AdmissionDepositAllocationStoreAdapter implements AdmissionDepositAllocationStorePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AdmissionDepositAllocationStoreAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override public Optional<DepositAllocatedPayload> lockAndFindReplay(AllocateAdmissionDepositCommand command) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Deposit allocation requires an active transaction");
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "admission-deposit-allocation-key:" + command.idempotencyKey());
        String intent = fingerprint(command);
        var rows = jdbc.queryForList("SELECT * FROM deposit_allocation_operation WHERE idempotency_key=?", command.idempotencyKey());
        if (rows.isEmpty()) return Optional.empty();
        var previous = rows.getFirst();
        if (!intent.equals(previous.get("intent_hash"))) throw new DuplicateResourceException(
                "BILLING_DEPOSIT_ALLOCATION_KEY_CONFLICT", "Idempotency key belongs to another allocation intent");
        try {
            return Optional.of(mapper.readValue(previous.get("payload").toString(), DepositAllocatedPayload.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalStateException("Cannot read immutable deposit allocation");
        }
    }

    @Override public void lockMedicallyClosedAccount(UUID accountId) {
        var account = jdbc.queryForList("SELECT * FROM billing_account WHERE account_id=? FOR UPDATE", accountId);
        if (account.isEmpty()) throw new com.mediflow.common.exception.ResourceNotFoundException("BILLING_ACCOUNT_NOT_FOUND", "Billing account not found");
        var row = account.getFirst();
        boolean closed = "CHARGE_CLOSED".equals(row.get("status")) || "SETTLEMENT_PENDING".equals(row.get("status"));
        boolean medical = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM admission_charge_close WHERE account_id=? AND admission_id=?
                    AND patient_id=? AND status='APPLIED')
                """, Boolean.class, accountId, row.get("care_episode_id"), row.get("patient_id")));
        if (!"ADMISSION".equals(row.get("care_episode_type")) || !closed || !medical) throw new BillingRuleException(
                "BILLING_DEPOSIT_ALLOCATION_NOT_READY", "Exact medically closed admission account required");
        // A concurrent repeatable-read caller must retry the entire fresh snapshot, not reuse old budgets.
        jdbc.update("UPDATE billing_account SET version=version+1 WHERE account_id=?", accountId);
    }

    @Override public void save(AllocateAdmissionDepositCommand command, DepositAllocatedPayload payload) {
        for (var line : payload.allocations()) {
            jdbc.update("INSERT INTO payment_allocation(allocation_id,transaction_id,charge_id,amount) VALUES (?,?,?,?)",
                    line.allocationId(), command.originalTransactionId(), line.chargeId(), line.amount());
        }
        try {
            jdbc.update("""
                    INSERT INTO deposit_allocation_operation(allocation_operation_id,account_id,original_transaction_id,
                        actor_id,idempotency_key,intent_hash,allocated_at,payload) VALUES (?,?,?,?,?,?,?,?)
                    """, payload.allocationOperationId(), command.accountId(), command.originalTransactionId(), command.actorId(),
                    command.idempotencyKey(), fingerprint(command), Timestamp.from(payload.allocatedAt()), mapper.writeValueAsString(payload));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalStateException("Cannot encode immutable deposit allocation");
        }
    }

    private String fingerprint(AllocateAdmissionDepositCommand command) {
        try {
            var amounts = new TreeMap<String, java.math.BigDecimal>();
            command.amounts().forEach((id, amount) -> amounts.put(id.toString(), amount.setScale(2, RoundingMode.UNNECESSARY)));
            var intent = Map.of("accountId", command.accountId().toString(), "actorId", command.actorId().toString(),
                    "originalTransactionId", command.originalTransactionId().toString(), "amounts", amounts);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(intent)));
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid exact deposit allocation intent"); }
    }
}
