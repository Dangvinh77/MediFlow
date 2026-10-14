package com.mediflow.organization.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.organization.application.event.SurgeryAuthorityChangedEvent;
import com.mediflow.organization.application.port.out.SurgeryAuthorityEventPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;

@Repository
public class SurgeryAuthorityOutboxAdapter implements SurgeryAuthorityEventPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SurgeryAuthorityOutboxAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void append(SurgeryAuthorityChangedEvent event) {
        try {
            jdbc.update("""
                    INSERT INTO surgery_authority_outbox(event_id,routing_key,payload,occurred_at)
                    VALUES(?,?,CAST(? AS jsonb),?)
                    """, event.eventId(), event.eventType(), mapper.writeValueAsString(event),
                    Timestamp.from(event.occurredAt()));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Unable to serialize authority event", failure);
        }
    }
}
