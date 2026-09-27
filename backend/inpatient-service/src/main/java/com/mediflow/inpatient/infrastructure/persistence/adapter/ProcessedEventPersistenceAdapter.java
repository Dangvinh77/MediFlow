package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.mediflow.inpatient.application.port.out.ProcessedEventPort;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "mediflow.inpatient.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class ProcessedEventPersistenceAdapter implements ProcessedEventPort {
    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryClaim(UUID eventId, String eventType) {
        return jdbcTemplate.update("""
                INSERT INTO su_kien_da_xu_ly(event_id, event_type)
                VALUES (?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, eventType) == 1;
    }
}
