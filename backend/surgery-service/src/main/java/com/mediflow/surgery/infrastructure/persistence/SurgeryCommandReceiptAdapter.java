package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@Profile("!test")
public class SurgeryCommandReceiptAdapter implements SurgeryCommandReceiptPort {

    private final JdbcTemplate jdbc;

    public SurgeryCommandReceiptAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Claim claim(Key key, String fingerprint) {
        requireTransaction();
        if (key == null || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Command fingerprint không hợp lệ");
        }
        UUID id = UUID.randomUUID();
        List<UUID> inserted = jdbc.query("""
                INSERT INTO surgery_command_receipt
                (receipt_id, actor_scope, command_code, idempotency_key, fingerprint, status)
                VALUES (?, ?, ?, ?, ?, 'PENDING')
                ON CONFLICT (actor_scope, command_code, idempotency_key) DO NOTHING
                RETURNING receipt_id
                """, (rs, ignored) -> rs.getObject(1, UUID.class), id,
                key.actorScope(), key.operation(), key.idempotencyKey(), fingerprint);
        if (!inserted.isEmpty()) return new Claim(State.NEW, id, null, null);
        return jdbc.queryForObject("""
                SELECT receipt_id, fingerprint, status, response_code, response_payload
                FROM surgery_command_receipt
                WHERE actor_scope = ? AND command_code = ? AND idempotency_key = ?
                FOR UPDATE
                """, (rs, ignored) -> {
            UUID existingId = rs.getObject("receipt_id", UUID.class);
            if (!fingerprint.equals(rs.getString("fingerprint"))) {
                return new Claim(State.CONFLICT, existingId, null, null);
            }
            if ("APPLIED".equals(rs.getString("status"))) {
                return new Claim(State.REPLAY, existingId,
                        rs.getString("response_code"), rs.getBytes("response_payload"));
            }
            return new Claim(State.IN_PROGRESS, existingId, null, null);
        }, key.actorScope(), key.operation(), key.idempotencyKey());
    }

    @Override
    public void complete(UUID receiptId, UUID caseId, String responseCode,
                         byte[] response, Instant at) {
        requireTransaction();
        if (receiptId == null || responseCode == null || responseCode.isBlank()
                || responseCode.length() > 64 || response == null || at == null) {
            throw new IllegalArgumentException("Command receipt outcome không hợp lệ");
        }
        int updated = jdbc.update("""
                UPDATE surgery_command_receipt
                SET surgery_case_id = ?, response_code = ?, response_payload = ?,
                    applied_at = ?, status = 'APPLIED'
                WHERE receipt_id = ? AND status = 'PENDING'
                """, caseId, responseCode, response.clone(), Timestamp.from(at), receiptId);
        if (updated != 1) throw new SurgeryRevisionConflictException();
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Command receipt yêu cầu application transaction");
        }
    }
}
