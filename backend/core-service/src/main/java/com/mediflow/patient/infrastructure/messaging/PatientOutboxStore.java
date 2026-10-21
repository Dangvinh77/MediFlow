package com.mediflow.patient.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** JDBC uses the connection enlisted by patientTransactionManager, never the primary Org store. */
@Repository
public class PatientOutboxStore {
    private final JdbcTemplate jdbc;

    public PatientOutboxStore(@Qualifier("patientDataSource") DataSource source) {
        jdbc = new JdbcTemplate(source);
    }

    public void append(UUID id, Instant occurredAt, String correlationId, String routingKey, String payload) {
        jdbc.update("""
                INSERT INTO patient_event_outbox
                    (event_id,occurred_at,correlation_id,routing_key,payload,status,attempts,next_attempt_at)
                VALUES (?,?,?,?,?,'PENDING',0,?)
                """, id, Timestamp.from(occurredAt), correlationId, routingKey, payload, Timestamp.from(Instant.now()));
    }

    public Optional<Delivery> lockNext() {
        return jdbc.query("""
                SELECT event_id,correlation_id,routing_key,payload,attempts FROM patient_event_outbox
                WHERE status='PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY next_attempt_at,occurred_at,event_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (row, index) -> new Delivery(row.getObject("event_id", UUID.class),
                row.getString("correlation_id"), row.getString("routing_key"), row.getString("payload"),
                row.getInt("attempts"))).stream().findFirst();
    }

    public void markPublished(UUID id) {
        jdbc.update("""
                UPDATE patient_event_outbox SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,last_error=NULL
                WHERE event_id=? AND status='PENDING'
                """, id);
    }

    public void markFailed(Delivery delivery, int maximumAttempts, String reason) {
        int attempts = delivery.attempts() + 1;
        long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
        jdbc.update("""
                UPDATE patient_event_outbox SET attempts=?,status=?,next_attempt_at=?,last_error=?
                WHERE event_id=? AND status='PENDING'
                """, attempts, attempts >= maximumAttempts ? "QUARANTINED" : "PENDING",
                Timestamp.from(Instant.now().plusSeconds(delaySeconds)), reason, delivery.id());
    }

    public record Delivery(UUID id, String correlationId, String routingKey, String payload, int attempts) {}
}
