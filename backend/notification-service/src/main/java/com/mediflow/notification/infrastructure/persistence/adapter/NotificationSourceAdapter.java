package com.mediflow.notification.infrastructure.persistence.adapter;

import com.mediflow.notification.application.port.out.NotificationSourcePort;
import com.mediflow.notification.domain.exception.NotificationEventConflictException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class NotificationSourceAdapter implements NotificationSourcePort {
    private final JdbcTemplate jdbc;
    public NotificationSourceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public boolean claim(String eventType, UUID sourceId, String sourceFingerprint) {
        int inserted = jdbc.update("INSERT INTO care_notification_source(event_type,source_id,payload_fingerprint) VALUES (?,?,?) ON CONFLICT(event_type,source_id) DO NOTHING",
                eventType, sourceId, sourceFingerprint);
        String previous = jdbc.queryForObject("SELECT payload_fingerprint FROM care_notification_source WHERE event_type=? AND source_id=?", String.class, eventType, sourceId);
        if (!sourceFingerprint.equals(previous)) throw new NotificationEventConflictException();
        return inserted == 1;
    }
}
