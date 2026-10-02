package com.mediflow.pharmacy.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.application.port.out.PrescriptionCareEventWriterPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;

/** Additive V1 writer; existing dispatcher and legacy publisher never activate held bytes. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PrescriptionCareEventWriterAdapter implements PrescriptionCareEventWriterPort {
    private final JdbcTemplate jdbc;
    private final PrescriptionCareEventCodec codec;

    public PrescriptionCareEventWriterAdapter(JdbcTemplate jdbc, PrescriptionCareEventCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public Optional<PrescriptionCareEvent> findHeld(UUID prescriptionId, EventType type) {
        var rows = jdbc.query("""
                SELECT payload FROM PHARMACY_EVENT_OUTBOX WHERE aggregate_id = ?
                    AND routing_key = ? AND care_contract_version = 1 AND NOT delivery_enabled
                """, (row, index) -> codec.decode(type.routingKey(), row.getString("payload").getBytes(StandardCharsets.UTF_8)),
                prescriptionId, type.routingKey());
        return rows.stream().findFirst();
    }

    @Override
    public void storeHeld(PrescriptionCareEvent event) {
        UUID prescriptionId = event.payload().prescriptionId();
        int order = event.eventType() == EventType.CREATED ? 0 : 1;
        if (order == 1 && !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM PHARMACY_EVENT_OUTBOX WHERE aggregate_id = ?
                    AND care_contract_version = 1 AND care_lifecycle_order = 0)
                """, Boolean.class, prescriptionId))) {
            throw new PrescriptionRuleException("PHARMACY_CARE_CREATED_EVENT_REQUIRED", "Persist the V1 creation before its terminal event");
        }
        String payload = new String(codec.encode(event), StandardCharsets.UTF_8);
        jdbc.update("""
                INSERT INTO PHARMACY_EVENT_OUTBOX(event_id, routing_key, aggregate_id, payload, created_at,
                    attempts, available_at, delivery_enabled, care_contract_version, care_lifecycle_order)
                VALUES (?, ?, ?, ?, clock_timestamp(), 0, clock_timestamp(), false, 1, ?)
                ON CONFLICT DO NOTHING
                """, event.eventId(), event.eventType().routingKey(), prescriptionId, payload, order);
        // Check both identity and semantic uniqueness. Never overwrite bytes/ID on re-delivery or collision.
        var rows = jdbc.query("""
                SELECT event_id, payload, delivery_enabled, care_contract_version FROM PHARMACY_EVENT_OUTBOX
                WHERE aggregate_id = ? AND care_contract_version = 1 AND care_lifecycle_order = ?
                """, (rs, row) -> new Stored(rs.getObject("event_id", UUID.class), rs.getString("payload"),
                rs.getBoolean("delivery_enabled"), rs.getShort("care_contract_version")), prescriptionId, order);
        if (rows.size() != 1 || !rows.get(0).eventId().equals(event.eventId()) || !rows.get(0).payload().equals(payload)
                || rows.get(0).enabled() || rows.get(0).version() != 1) {
            throw new PrescriptionRuleException("PHARMACY_CARE_EVENT_CONFLICT", "Immutable V1 lifecycle event conflict");
        }
    }

    private record Stored(UUID eventId, String payload, boolean enabled, short version) { }
}
