package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.port.out.SurgeryOutboxPort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** No Rabbit call occurs here; a gated dispatcher must handle confirms and returns. */
@Repository
@Profile("!test")
public class SurgeryOutboxAdapter implements SurgeryOutboxPort {

    private static final int MAX_ATTEMPTS = 10;
    private static final long MAX_BACKOFF_SECONDS = 300;

    private final JdbcTemplate jdbc;

    public SurgeryOutboxAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(OutgoingEvent event) {
        requireTransaction();
        if (event == null) throw new IllegalArgumentException("Outbox event không được null");
        jdbc.update("""
                INSERT INTO surgery_outbox
                (event_id, surgery_case_id, aggregate_revision, sequence_no,
                 event_type, event_version, correlation_id, payload,
                 status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, event.eventId(), event.caseId(), event.aggregateRevision(), event.order(),
                event.eventType(), event.eventVersion(), event.correlationId(),
                event.payload(), Timestamp.from(event.occurredAt()));
    }

    @Override
    public Optional<Delivery> claimNext(Instant now, Duration lease) {
        requireTransaction();
        if (now == null || lease == null || lease.isNegative() || lease.isZero()
                || lease.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("Outbox lease phải trong khoảng (0, 10 phút]");
        }
        jdbc.update("""
                UPDATE surgery_outbox
                SET status = 'QUARANTINED', claim_token = NULL, lease_until = NULL,
                    reason = 'LEASE_EXHAUSTED'
                WHERE status = 'CLAIMED' AND lease_until <= ? AND attempt_count >= ?
                """, Timestamp.from(now), MAX_ATTEMPTS);
        List<UUID> eligible = jdbc.query("""
                SELECT o.event_id FROM surgery_outbox o
                WHERE ((o.status = 'PENDING'
                           AND (o.next_attempt_at IS NULL OR o.next_attempt_at <= ?))
                       OR (o.status = 'CLAIMED' AND o.lease_until <= ?))
                  AND o.attempt_count < ?
                  AND NOT EXISTS (
                    SELECT 1 FROM surgery_outbox earlier
                    WHERE earlier.surgery_case_id = o.surgery_case_id
                      AND (earlier.aggregate_revision < o.aggregate_revision
                           OR (earlier.aggregate_revision = o.aggregate_revision
                               AND earlier.sequence_no < o.sequence_no))
                      AND earlier.status <> 'PUBLISHED')
                ORDER BY o.created_at, o.event_id
                LIMIT 1 FOR UPDATE OF o SKIP LOCKED
                """, (rs, ignored) -> rs.getObject(1, UUID.class),
                Timestamp.from(now), Timestamp.from(now), MAX_ATTEMPTS);
        if (eligible.isEmpty()) return Optional.empty();
        UUID eventId = eligible.getFirst();
        UUID token = UUID.randomUUID();
        Instant until = now.plus(lease);
        int updated = jdbc.update("""
                UPDATE surgery_outbox
                SET status = 'CLAIMED', claim_token = ?, lease_until = ?,
                    attempt_count = attempt_count + 1, reason = NULL
                WHERE event_id = ?
                """, token, Timestamp.from(until), eventId);
        if (updated != 1) throw new IllegalStateException("Outbox claim lost its row lock");
        return Optional.of(jdbc.queryForObject("""
                SELECT * FROM surgery_outbox WHERE event_id = ?
                """, (rs, ignored) -> new Delivery(
                rs.getObject("event_id", UUID.class),
                rs.getObject("surgery_case_id", UUID.class),
                rs.getLong("aggregate_revision"), rs.getInt("sequence_no"),
                rs.getString("event_type"), rs.getInt("event_version"),
                rs.getString("correlation_id"), rs.getBytes("payload"),
                token, until, rs.getInt("attempt_count")), eventId));
    }

    @Override
    public boolean markPublished(UUID eventId, UUID attemptToken, Instant at) {
        requireTransaction();
        if (eventId == null || attemptToken == null || at == null) {
            throw new IllegalArgumentException("Outbox confirm identity không hợp lệ");
        }
        return jdbc.update("""
                UPDATE surgery_outbox
                SET status = 'PUBLISHED', published_at = ?,
                    claim_token = NULL, lease_until = NULL, next_attempt_at = NULL
                WHERE event_id = ? AND claim_token = ? AND status = 'CLAIMED'
                  AND lease_until > ?
                """, Timestamp.from(at), eventId, attemptToken, Timestamp.from(at)) == 1;
    }

    @Override
    public boolean markReturned(UUID eventId, UUID attemptToken, String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Outbox return cần reason");
        }
        return retry(eventId, attemptToken, "RETURNED: " + reason, at);
    }

    @Override
    public boolean retry(UUID eventId, UUID attemptToken, String reason, Instant at) {
        requireTransaction();
        if (eventId == null || attemptToken == null || reason == null || reason.isBlank()
                || reason.length() > 1000 || at == null) {
            throw new IllegalArgumentException("Outbox retry identity không hợp lệ");
        }
        List<Integer> attempts = jdbc.query("""
                SELECT attempt_count FROM surgery_outbox
                WHERE event_id = ? AND claim_token = ? AND status = 'CLAIMED'
                  AND lease_until > ?
                FOR UPDATE
                """, (rs, ignored) -> rs.getInt(1), eventId, attemptToken, Timestamp.from(at));
        if (attempts.isEmpty()) return false;
        int count = attempts.getFirst();
        if (count >= MAX_ATTEMPTS) {
            return jdbc.update("""
                    UPDATE surgery_outbox
                    SET status = 'QUARANTINED', reason = ?, claim_token = NULL,
                        lease_until = NULL, next_attempt_at = NULL
                    WHERE event_id = ? AND claim_token = ? AND status = 'CLAIMED'
                    """, reason, eventId, attemptToken) == 1;
        }
        long seconds = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(count, 8));
        return jdbc.update("""
                UPDATE surgery_outbox
                SET status = 'PENDING', reason = ?, claim_token = NULL,
                    lease_until = NULL, next_attempt_at = ?
                WHERE event_id = ? AND claim_token = ? AND status = 'CLAIMED'
                """, reason, Timestamp.from(at.plusSeconds(seconds)), eventId, attemptToken) == 1;
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Surgery outbox yêu cầu application transaction");
        }
    }
}
