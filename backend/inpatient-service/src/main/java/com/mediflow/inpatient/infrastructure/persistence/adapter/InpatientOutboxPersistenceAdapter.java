package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.inpatient.application.dto.event.DomainEventEnvelope;
import com.mediflow.inpatient.application.port.out.InpatientOutboxPort;
import com.mediflow.inpatient.infrastructure.messaging.InpatientEventWireMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@ConditionalOnProperty(name = "mediflow.inpatient.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class InpatientOutboxPersistenceAdapter implements InpatientOutboxPort {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final InpatientEventWireMapper wireMapper;

    public InpatientOutboxPersistenceAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                             InpatientEventWireMapper wireMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.wireMapper = wireMapper;
    }

    @Override
    public void append(UUID aggregateId, DomainEventEnvelope<?> event) {
        try {
            String payload = objectMapper.writeValueAsString(wireMapper.toWireEnvelope(event));
            jdbcTemplate.update("""
                    INSERT INTO su_kien_outbox_noi_tru
                      (event_id, aggregate_type, aggregate_id, event_type, event_version,
                       correlation_id, payload, occurred_at)
                    VALUES (?, 'ADMISSION', ?, ?, ?, ?, CAST(? AS jsonb), ?)
                    """, event.maSuKien(), aggregateId, event.loaiSuKien(), event.phienBan(),
                    event.maTuongQuan(), payload, event.xayRaLuc());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Inpatient event could not be serialized", exception);
        }
    }

    @Override
    public List<PendingOutboxEvent> claimPending(int limit) {
        int batchSize = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("""
                SELECT event_id, event_type, payload::text
                FROM su_kien_outbox_noi_tru
                WHERE published_at IS NULL
                ORDER BY occurred_at, event_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """, (row, index) -> new PendingOutboxEvent(
                row.getObject("event_id", UUID.class), row.getString("event_type"), row.getString("payload")),
                batchSize);
    }

    @Override
    public void markPublished(UUID eventId, Instant publishedAt) {
        jdbcTemplate.update("UPDATE su_kien_outbox_noi_tru SET published_at = ? WHERE event_id = ?",
                publishedAt, eventId);
    }

    @Override
    public void markPublishFailed(UUID eventId, String reason) {
        String safeReason = reason == null ? "publish failed" : reason;
        if (safeReason.length() > 500) {
            safeReason = safeReason.substring(0, 500);
        }
        jdbcTemplate.update("""
                UPDATE su_kien_outbox_noi_tru
                SET retry_count = retry_count + 1, last_error = ?
                WHERE event_id = ? AND published_at IS NULL
                """, safeReason, eventId);
    }
}
