package com.mediflow.notification.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.domain.model.CareNotificationIntent;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class CareNotificationRepositoryAdapter implements CareNotificationRepositoryPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public CareNotificationRepositoryAdapter(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Override public boolean claim(UUID eventId, String eventType, String fingerprint) {
        int inserted = jdbc.update("""
                INSERT INTO PROCESSED_EVENT(event_id,routing_key,payload_hash) VALUES (?,?,?)
                ON CONFLICT(event_id) DO NOTHING
                """, eventId, eventType, fingerprint);
        if (inserted == 1) return true;
        var previous = jdbc.queryForMap("SELECT routing_key,payload_hash FROM PROCESSED_EVENT WHERE event_id=?", eventId);
        if (!eventType.equals(previous.get("routing_key")) || !fingerprint.equals(previous.get("payload_hash")))
            throw new com.mediflow.notification.domain.exception.NotificationEventConflictException();
        return false;
    }

    @Override public void deliverInApp(CareNotificationIntent intent) {
        jdbc.update("""
                INSERT INTO NOTIFICATION(notification_id,patient_id,title,content,channel,status,sent_at,
                    template_key,source_event_id,source_event_type,source_id,correlation_id,sensitivity)
                VALUES (?,?,?,?,'IN_APP','SENT',now(),?,?,?,?,?,'PRIVATE_IN_APP_ONLY')
                """, intent.notificationId(), intent.patientId(), intent.title(), intent.content(), intent.templateKey(),
                intent.sourceEventId(), intent.sourceEventType(), intent.sourceId(), intent.correlationId());
        // IN_APP delivery is the committed, authenticated history row, so no external sender is needed.
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            String body = mapper.writeValueAsString(Map.of("eventId", eventId, "eventType", "notification.sent", "version", 1,
                    "occurredAt", now, "correlationId", intent.correlationId(), "producer", "notification-service", "payload",
                    Map.of("notificationId", intent.notificationId(), "patientId", intent.patientId(), "channel", "IN_APP",
                            "status", "SENT", "templateKey", intent.templateKey(), "sourceEventId", intent.sourceEventId())));
            jdbc.update("INSERT INTO NOTIFICATION_EVENT_OUTBOX(event_id,notification_id,payload) VALUES (?,?,?)",
                    eventId, intent.notificationId(), body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize notification delivery fact", exception);
        }
    }
}
