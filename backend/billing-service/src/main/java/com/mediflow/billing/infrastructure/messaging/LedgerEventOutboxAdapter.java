package com.mediflow.billing.infrastructure.messaging;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.port.out.LedgerEventPort;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class LedgerEventOutboxAdapter implements LedgerEventPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public LedgerEventOutboxAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }
    @Override
    public void appendHeld(UUID accountId, LedgerIntegrationEvent event) {
        try {
            jdbc.update("""
                    INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version)
                    VALUES (?,?,?,?,FALSE,1)
                    """, event.eventId(), event.eventType(), accountId, mapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize immutable ledger event", exception);
        }
    }
}
