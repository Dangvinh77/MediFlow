package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;
import com.mediflow.clinical.application.exception.ConsumerInboxConflictException;
import com.mediflow.clinical.application.port.out.ConsumerInboxPort;
import com.mediflow.clinical.application.port.out.ConsumerInboxPort.InboxOutcome;

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
        if (key.startsWith("clinical:lab-result:v0:")) {
            UUID labId = uuidPart(key.substring("clinical:lab-result:v0:".length()));
            lockLabReference(labId);
            return exists("SELECT EXISTS(SELECT 1 FROM attached_result WHERE type = 'LAB' AND reference_id = ?)",
                    labId);
        }
        if (key.startsWith("clinical:lab-result:v1:")) {
            UUID labId = uuidPart(key.substring("clinical:lab-result:v1:".length(), key.length() - 2));
            lockLabReference(labId);
            return exists("SELECT EXISTS(SELECT 1 FROM attached_result WHERE type = 'LAB' AND reference_id = ?)",
                    labId);
        }
        if (key.startsWith("clinical:prescription:v0:")) {
            lockPrescriptionReference(uuidPart(key.substring("clinical:prescription:v0:".length())));
            return exists("SELECT EXISTS(SELECT 1 FROM attached_result "
                    + "WHERE type = 'PRESCRIPTION' AND reference_id = ?)",
                    uuidPart(key.substring("clinical:prescription:v0:".length())));
        }
        if (key.startsWith("clinical:prescription:v1:")) {
            UUID prescriptionId = claim.legacyEffectIds().getFirst();
            lockPrescriptionReference(prescriptionId);
            return exists("SELECT EXISTS(SELECT 1 FROM attached_result "
                    + "WHERE type = 'PRESCRIPTION' AND reference_id = ?)", prescriptionId);
        }
        if (key.startsWith("clinical:clearance:v1:")) {
            return exists("SELECT EXISTS(SELECT 1 FROM exam_clearance WHERE clearance_id = ?)",
                    uuidPart(key.substring("clinical:clearance:v1:".length())));
        }
        return false;
    }

    private void lockLabReference(UUID labId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0)) IS NULL",
                Boolean.class, "clinical:lab-result-reference:" + labId);
    }

    private void lockPrescriptionReference(UUID prescriptionId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0)) IS NULL",
                Boolean.class, "clinical:prescription-reference:" + prescriptionId);
    }

    private void requireKnownContract(ConsumerInboxClaim claim) {
        String key = claim.operationKey();
        boolean known = ("clinical-lab-result".equals(claim.consumerName())
                    && "lab.result.created".equals(claim.routingKey())
                    && (key.startsWith("clinical:lab-result:v0:") || key.matches(
                            "clinical:lab-result:v1:[0-9a-fA-F-]{36}:1")))
                || ("clinical-prescription".equals(claim.consumerName())
                    && "prescription.filled".equals(claim.routingKey())
                    && (key.matches("clinical:prescription:v0:[0-9a-fA-F-]{36}")
                        || key.matches("clinical:prescription:v1:[0-9a-fA-F-]{36}")
                            && claim.legacyEffectIds().size() == 1))
                || ("clinical-exam-clearance".equals(claim.consumerName())
                    && "financial.clearance.granted".equals(claim.routingKey())
                    && key.matches("clinical:clearance:v1:[0-9a-fA-F-]{36}"))
                || ("clinical-outpatient-eligibility".equals(claim.consumerName())
                    && "outpatient.financial.eligibility.granted".equals(claim.routingKey())
                    && key.matches("clinical:outpatient-eligibility:v1:[0-9a-fA-F-]{36}:[1-9][0-9]{0,18}:COMPLETED"));
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
