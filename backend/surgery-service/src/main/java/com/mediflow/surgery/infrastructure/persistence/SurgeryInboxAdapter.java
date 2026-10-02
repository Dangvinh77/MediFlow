package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Repository
@Profile("!test")
public class SurgeryInboxAdapter implements SurgeryInboxPort {

    private final JdbcTemplate jdbc;

    public SurgeryInboxAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Decision begin(IncomingEvent event) {
        requireTransaction();
        if (event == null) throw new IllegalArgumentException("Inbox event không được null");
        lockSemanticKey(event.semanticKey());
        List<UUID> inserted = jdbc.query("""
                INSERT INTO surgery_inbox
                (event_id, event_type, event_version, system_producer,
                 fingerprint, semantic_key, payload, status, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                ON CONFLICT (event_id) DO NOTHING RETURNING event_id
                """, (rs, ignored) -> rs.getObject(1, UUID.class), event.eventId(),
                event.eventType(), event.version(), event.producer(), event.fingerprint(),
                event.semanticKey(), event.payload(), Timestamp.from(event.receivedAt()));
        StoredEvent stored = loadForUpdate(event.eventId());
        if (!sameEventContent(stored, event)) {
            recordConflict(event, "EVENT_ID_CONTENT_MISMATCH");
            return Decision.CONFLICT;
        }
        if (inserted.isEmpty()) {
            return switch (stored.status()) {
                case "APPLIED" -> Decision.ALREADY_APPLIED;
                case "QUARANTINED" -> Decision.QUARANTINED;
                default -> Decision.RETRY_PENDING;
            };
        }
        List<StoredEvent> applied = jdbc.query("""
                SELECT event_id, event_type, event_version, system_producer,
                       fingerprint, semantic_key, payload, status
                FROM surgery_inbox
                WHERE semantic_key = ? AND status = 'APPLIED' AND event_id <> ?
                """, (rs, ignored) -> row(rs), event.semanticKey(), event.eventId());
        if (!applied.isEmpty()) {
            StoredEvent prior = applied.getFirst();
            if (sameEventContent(prior, event)) {
                jdbc.update("""
                        UPDATE surgery_inbox SET status = 'APPLIED', applied_at = ?
                        WHERE event_id = ?
                        """, Timestamp.from(event.receivedAt()), event.eventId());
                return Decision.ALREADY_APPLIED;
            }
            jdbc.update("""
                    UPDATE surgery_inbox
                    SET status = 'QUARANTINED', reason = 'SEMANTIC_CONTENT_MISMATCH'
                    WHERE event_id = ?
                    """, event.eventId());
            return Decision.CONFLICT;
        }
        return Decision.NEW;
    }

    @Override
    public void markApplied(UUID eventId, Instant at) {
        requireTransaction();
        if (eventId == null || at == null) throw new IllegalArgumentException("Event/time bắt buộc");
        String semanticKey = jdbc.queryForObject("""
                SELECT semantic_key FROM surgery_inbox WHERE event_id = ?
                """, String.class, eventId);
        lockSemanticKey(semanticKey);
        StoredEvent pending = loadForUpdate(eventId);
        if (!"PENDING".equals(pending.status())) throw new SurgeryRevisionConflictException();
        Integer otherApplied = jdbc.queryForObject("""
                SELECT count(*) FROM surgery_inbox
                WHERE semantic_key = ? AND event_id <> ? AND status = 'APPLIED'
                """, Integer.class, pending.semanticKey(), eventId);
        if (otherApplied != null && otherApplied > 0) throw new SurgeryRevisionConflictException();
        int updated = jdbc.update("""
                UPDATE surgery_inbox
                SET status = 'APPLIED', applied_at = ?, reason = NULL,
                    next_attempt_at = NULL
                WHERE event_id = ? AND status = 'PENDING'
                """, Timestamp.from(at), eventId);
        if (updated != 1) throw new SurgeryRevisionConflictException();
    }

    @Override
    public void defer(UUID eventId, String reason, Instant nextAttempt) {
        requireTransaction();
        if (eventId == null || reason == null || reason.isBlank() || nextAttempt == null) {
            throw new IllegalArgumentException("Pending event cần reason và thời điểm retry");
        }
        int updated = jdbc.update("""
                UPDATE surgery_inbox
                SET reason = ?, attempt_count = attempt_count + 1, next_attempt_at = ?
                WHERE event_id = ? AND status = 'PENDING'
                """, reason, Timestamp.from(nextAttempt), eventId);
        if (updated != 1) throw new SurgeryRevisionConflictException();
    }

    @Override
    public void quarantine(UUID eventId, String reason) {
        requireTransaction();
        if (eventId == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Quarantine event cần reason");
        }
        int updated = jdbc.update("""
                UPDATE surgery_inbox
                SET status = 'QUARANTINED', reason = ?, next_attempt_at = NULL
                WHERE event_id = ? AND status = 'PENDING'
                """, reason, eventId);
        if (updated != 1) throw new SurgeryRevisionConflictException();
    }

    private void lockSemanticKey(String key) {
        jdbc.update("""
                INSERT INTO surgery_inbox_semantic_mutex (semantic_key)
                VALUES (?) ON CONFLICT (semantic_key) DO NOTHING
                """, key);
        jdbc.queryForObject("""
                SELECT semantic_key FROM surgery_inbox_semantic_mutex
                WHERE semantic_key = ? FOR UPDATE
                """, String.class, key);
    }

    private StoredEvent loadForUpdate(UUID eventId) {
        return jdbc.queryForObject("""
                SELECT event_id, event_type, event_version, system_producer,
                       fingerprint, semantic_key, payload, status
                FROM surgery_inbox WHERE event_id = ? FOR UPDATE
                """, (rs, ignored) -> row(rs), eventId);
    }

    private void recordConflict(IncomingEvent event, String reason) {
        jdbc.update("""
                INSERT INTO surgery_inbox_conflict
                (conflict_id, event_id, fingerprint, payload, reason, received_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), event.eventId(), event.fingerprint(),
                event.payload(), reason, Timestamp.from(event.receivedAt()));
    }

    private static StoredEvent row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoredEvent(rs.getObject("event_id", UUID.class),
                rs.getString("event_type"), rs.getInt("event_version"),
                rs.getString("system_producer"), rs.getString("fingerprint"),
                rs.getString("semantic_key"), rs.getBytes("payload"),
                rs.getString("status"));
    }

    private static boolean sameEventContent(StoredEvent stored, IncomingEvent incoming) {
        return sameBusinessIdentity(stored, incoming)
                && Arrays.equals(stored.payload(), incoming.payload());
    }

    private static boolean sameBusinessIdentity(StoredEvent stored, IncomingEvent incoming) {
        return stored.type().equals(incoming.eventType())
                && stored.version() == incoming.version()
                && stored.producer().equals(incoming.producer())
                && stored.fingerprint().equals(incoming.fingerprint())
                && stored.semanticKey().equals(incoming.semanticKey());
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Surgery inbox yêu cầu application transaction");
        }
    }

    private record StoredEvent(UUID id, String type, int version, String producer,
                               String fingerprint, String semanticKey, byte[] payload, String status) {
    }
}
