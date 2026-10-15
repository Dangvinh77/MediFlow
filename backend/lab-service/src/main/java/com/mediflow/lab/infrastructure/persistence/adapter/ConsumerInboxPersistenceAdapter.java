package com.mediflow.lab.infrastructure.persistence.adapter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.lab.application.dto.command.ConsumerInboxClaim;
import com.mediflow.lab.application.exception.ConsumerInboxConflictException;
import com.mediflow.lab.application.port.out.ConsumerInboxPort;
import com.mediflow.lab.application.port.out.ConsumerInboxPort.InboxOutcome;

/** PostgreSQL inbox implementation. Callers keep this claim and the effect in one transaction. */
@Component
public class ConsumerInboxPersistenceAdapter implements ConsumerInboxPort {
    private static final String DELIVERY_INSERT = """
            INSERT INTO consumer_inbox_delivery (
                consumer_name, event_id, routing_key, operation_key, raw_body, raw_sha256,
                canonical_business_bytes, canonical_sha256
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;
    private static final String OPERATION_INSERT = """
            INSERT INTO consumer_inbox_operation (
                consumer_name, operation_key, first_event_id, canonical_business_bytes, canonical_sha256
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (consumer_name, operation_key) DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public ConsumerInboxPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public InboxOutcome claim(ConsumerInboxClaim claim) {
        requireKnownContract(claim);
        byte[] raw = claim.rawBody();
        byte[] canonical = claim.canonicalBusinessBytes();
        String rawHash = sha256(raw);
        String canonicalHash = sha256(canonical);

        int deliveryInserted = jdbc.update(DELIVERY_INSERT, claim.consumerName(), claim.eventId(),
                claim.routingKey(), claim.operationKey(), raw, rawHash, canonical, canonicalHash);
        if (deliveryInserted == 0) {
            StoredDelivery stored = loadDelivery(claim.eventId());
            if (stored == null || !stored.consumerName().equals(claim.consumerName())
                    || !stored.routingKey().equals(claim.routingKey())
                    || !stored.operationKey().equals(claim.operationKey())
                    || !stored.rawHash().equals(rawHash) || !MessageDigest.isEqual(stored.raw(), raw)
                    || !stored.canonicalHash().equals(canonicalHash)
                    || !MessageDigest.isEqual(stored.canonical(), canonical)) {
                throw conflict("eventId was previously recorded with different delivery evidence");
            }
            return InboxOutcome.DUPLICATE_EVENT;
        }

        if (exists("SELECT EXISTS(SELECT 1 FROM processed_event WHERE event_id = ?)", claim.eventId())) {
            throw conflict("eventId has a legacy receipt with no verifiable raw evidence");
        }

        int operationInserted = jdbc.update(OPERATION_INSERT, claim.consumerName(), claim.operationKey(),
                claim.eventId(), canonical, canonicalHash);
        if (operationInserted == 0) {
            StoredOperation stored = loadOperation(claim.consumerName(), claim.operationKey());
            if (stored == null || !stored.canonicalHash().equals(canonicalHash)
                    || !MessageDigest.isEqual(stored.canonical(), canonical)) {
                throw conflict("operation key was previously recorded with different business evidence");
            }
            return InboxOutcome.DUPLICATE_OPERATION;
        }

        if (hasUnverifiableLegacyEffect(claim)) {
            throw conflict("operation has a historical effect with no verifiable fingerprint");
        }
        return InboxOutcome.APPLIED;
    }

    private boolean hasUnverifiableLegacyEffect(ConsumerInboxClaim claim) {
        String key = claim.operationKey();
        if (key.startsWith("lab:financial-clearance:v1:")) {
            UUID clearanceId = uuidPart(key.substring("lab:financial-clearance:v1:".length()));
            lockLabTargets(claim.legacyEffectIds());
            return exists("SELECT EXISTS(SELECT 1 FROM lab_financial_clearance WHERE clearance_id = ?)",
                    clearanceId);
        }
        if (key.startsWith("lab:payment:v0:")) {
            for (UUID testId : claim.legacyEffectIds().stream().sorted().toList()) {
                lockLabTarget(testId);
                if (exists("SELECT EXISTS(SELECT 1 FROM lab_test WHERE test_id = ? AND is_paid = TRUE)", testId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void lockLabTargets(List<UUID> testIds) {
        testIds.stream().sorted().forEach(this::lockLabTarget);
    }

    private void lockLabTarget(UUID testId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0)) IS NULL",
                Boolean.class, "lab:inbox-target:" + testId);
        jdbc.query("SELECT test_id FROM lab_test WHERE test_id = ? FOR UPDATE", rs -> { }, testId);
    }

    private void requireKnownContract(ConsumerInboxClaim claim) {
        String key = claim.operationKey();
        boolean known = ("lab-medical-record".equals(claim.consumerName())
                    && "medicalrecord.created".equals(claim.routingKey())
                    && (key.matches("lab:medical-record:v0:[0-9a-fA-F-]{36}")
                        || key.matches("lab:medical-record:v1:[0-9a-fA-F-]{36}")))
                || ("lab-payment".equals(claim.consumerName())
                    && "payment.completed".equals(claim.routingKey())
                    && key.matches("lab:payment:v0:[0-9a-fA-F-]{36}"))
                || ("lab-test-clearance".equals(claim.consumerName())
                    && "financial.clearance.granted".equals(claim.routingKey())
                    && key.matches("lab:financial-clearance:v1:[0-9a-fA-F-]{36}"));
        if (!known) {
            throw conflict("unknown consumer contract or operation key");
        }
    }

    private StoredDelivery loadDelivery(UUID eventId) {
        List<StoredDelivery> rows = jdbc.query("""
                SELECT consumer_name, routing_key, operation_key, raw_body, raw_sha256,
                       canonical_business_bytes, canonical_sha256
                FROM consumer_inbox_delivery WHERE event_id = ?
                """, (rs, row) -> new StoredDelivery(rs.getString("consumer_name"), rs.getString("routing_key"),
                rs.getString("operation_key"),
                rs.getBytes("raw_body"), rs.getString("raw_sha256"),
                rs.getBytes("canonical_business_bytes"), rs.getString("canonical_sha256")), eventId);
        return rows.stream().findFirst().orElse(null);
    }

    private StoredOperation loadOperation(String consumer, String operationKey) {
        List<StoredOperation> rows = jdbc.query("""
                SELECT canonical_business_bytes, canonical_sha256
                FROM consumer_inbox_operation WHERE consumer_name = ? AND operation_key = ?
                """, (rs, row) -> new StoredOperation(rs.getBytes("canonical_business_bytes"),
                rs.getString("canonical_sha256")), consumer, operationKey);
        return rows.stream().findFirst().orElse(null);
    }

    private boolean exists(String sql, Object value) {
        Boolean found = jdbc.queryForObject(sql, Boolean.class, value);
        return Boolean.TRUE.equals(found);
    }

    private static UUID uuidPart(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException invalidKey) {
            throw conflict("operation key contains an invalid identifier");
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static ConsumerInboxConflictException conflict(String message) {
        return new ConsumerInboxConflictException(message);
    }

    private record StoredDelivery(String consumerName, String routingKey, String operationKey,
                                  byte[] raw, String rawHash,
                                 byte[] canonical, String canonicalHash) { }

    private record StoredOperation(byte[] canonical, String canonicalHash) { }
}
