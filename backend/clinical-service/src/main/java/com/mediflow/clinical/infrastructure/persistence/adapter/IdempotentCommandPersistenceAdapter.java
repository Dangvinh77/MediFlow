package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.io.IOException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.common.exception.DuplicateResourceException;

@Component
public class IdempotentCommandPersistenceAdapter implements IdempotentCommandPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IdempotentCommandPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public <T> Result<T> execute(String actorKey, String method, String canonicalPath, String idempotencyKey,
            String requestFingerprint, Class<T> responseType, int successStatus,
            java.util.function.Function<T, String> location, java.util.function.Supplier<T> command) {
        lock(actorKey, method, canonicalPath, idempotencyKey);
        List<StoredReceipt> rows = jdbc.query("""
                SELECT request_fingerprint, response_status, location, response_json::text AS response_json
                FROM clinical_scheduling_command_receipt
                WHERE actor_key = ? AND http_method = ? AND canonical_path = ? AND idempotency_key = ?
                """, (rs, row) -> new StoredReceipt(rs.getString("request_fingerprint"),
                rs.getInt("response_status"), rs.getString("location"), rs.getString("response_json")),
                actorKey, method, canonicalPath, idempotencyKey);
        if (!rows.isEmpty()) {
            StoredReceipt receipt = rows.get(0);
            if (!receipt.fingerprint().equals(requestFingerprint)) {
                throw new DuplicateResourceException("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with different input");
            }
            try {
                return new Result<>(objectMapper.readValue(receipt.responseJson(), responseType),
                        receipt.status(), receipt.location(), true);
            } catch (IOException invalidReceipt) {
                throw new IllegalStateException("Stored scheduling command receipt is malformed", invalidReceipt);
            }
        }

        T data = command.get();
        try {
            jdbc.update("""
                    INSERT INTO clinical_scheduling_command_receipt(
                        actor_key, http_method, canonical_path, idempotency_key, request_fingerprint,
                        response_status, location, response_json, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    """, actorKey, method, canonicalPath, idempotencyKey, requestFingerprint,
                    successStatus, location.apply(data), objectMapper.writeValueAsString(data), Timestamp.from(Instant.now()));
        } catch (IOException serializationFailure) {
            throw new IllegalStateException("Scheduling command receipt could not be serialized", serializationFailure);
        }
        return new Result<>(data, successStatus, location.apply(data), false);
    }

    private void lock(String actorKey, String method, String path, String key) {
        String lockKey = actorKey + "\n" + method + "\n" + path + "\n" + key;
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {
            rs.next();
            return null;
        }, lockKey);
    }

    private record StoredReceipt(String fingerprint, int status, String location, String responseJson) {}
}
