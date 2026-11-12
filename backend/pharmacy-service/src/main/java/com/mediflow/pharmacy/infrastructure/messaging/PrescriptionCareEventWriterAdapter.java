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

/** Immutable V1 writer: explicit new outpatient cohorts can deliver; historical HELD never enrolls by replay. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PrescriptionCareEventWriterAdapter implements PrescriptionCareEventWriterPort {
    private final JdbcTemplate jdbc;
    private final PrescriptionCareEventCodec codec;
    private final OutpatientEventActivation activation;

    public PrescriptionCareEventWriterAdapter(JdbcTemplate jdbc, PrescriptionCareEventCodec codec) {
        this(jdbc,codec,null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PrescriptionCareEventWriterAdapter(JdbcTemplate jdbc, PrescriptionCareEventCodec codec,
            @org.springframework.lang.Nullable OutpatientEventActivation activation) {
        this.jdbc = jdbc;
        this.codec = codec;
        this.activation=activation;
    }

    @Override
    public Optional<PrescriptionCareEvent> findHeld(UUID prescriptionId, EventType type) {
        var rows = jdbc.query("""
                SELECT payload FROM PHARMACY_EVENT_OUTBOX WHERE aggregate_id = ?
                    AND routing_key = ? AND care_contract_version = 1
                """, (row, index) -> codec.decode(type.routingKey(), row.getString("payload").getBytes(StandardCharsets.UTF_8)),
                prescriptionId, type.routingKey());
        return rows.stream().findFirst();
    }

    @Override
    public void storeHeld(PrescriptionCareEvent event) {
        UUID prescriptionId = event.payload().prescriptionId();
        int order = eventOrder(event);
        if (order > 0 && !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM PHARMACY_EVENT_OUTBOX WHERE aggregate_id = ?
                    AND care_contract_version = 1 AND care_lifecycle_order = ?)
                """, Boolean.class, prescriptionId, order - 1))) {
            if (order == 1) {
                throw new PrescriptionRuleException("PHARMACY_CARE_CREATED_EVENT_REQUIRED",
                        "Persist the V1 creation before its revision or terminal event");
            }
            throw new PrescriptionRuleException("PHARMACY_CARE_PREDECESSOR_EVENT_REQUIRED",
                    "Persist every earlier V1 prescription event before its next revision or terminal event");
        }
        String payload = new String(codec.encode(event), StandardCharsets.UTF_8);
        var existing=jdbc.query("SELECT event_id,payload,delivery_enabled,care_contract_version,care_delivery_cohort_id FROM pharmacy_event_outbox WHERE aggregate_id=? AND care_contract_version=1 AND care_lifecycle_order=?",
                (rs,n)->new Stored(rs.getObject("event_id",UUID.class),rs.getString("payload"),rs.getBoolean("delivery_enabled"),
                        rs.getShort("care_contract_version"),rs.getObject("care_delivery_cohort_id",UUID.class)),prescriptionId,order);
        if(!existing.isEmpty()) {
            var stored=existing.getFirst();
            if(existing.size()!=1 || !stored.eventId().equals(event.eventId()) || !stored.payload().equals(payload)
                    || stored.version()!=1 || stored.enabled()!=(stored.cohort()!=null)
                    || stored.cohort()!=null && !prescriptionId.equals(stored.cohort()))
                throw new PrescriptionRuleException("PHARMACY_CARE_EVENT_CONFLICT","Immutable V1 lifecycle event conflict");
            return; // Replay never enrolls historical HELD, even after a flag change.
        }
        if(order==0 && activation!=null && activation.enabled()) {
            var eligible=jdbc.queryForList("SELECT prescription_id FROM prescription WHERE prescription_id=? AND care_contract_version=1 AND care_context='OUTPATIENT' FOR UPDATE",UUID.class,prescriptionId);
            if(eligible.size()!=1) throw new PrescriptionRuleException("PHARMACY_CARE_EVENT_CONFLICT","Only a persisted outpatient aggregate can join the new delivery cohort");
            jdbc.update("INSERT INTO pharmacy_outpatient_delivery_cohort(prescription_id,acceptance_reference) VALUES (?,?) ON CONFLICT DO NOTHING",prescriptionId,activation.reference());
        }
        var cohorts=jdbc.queryForList("SELECT prescription_id FROM pharmacy_outpatient_delivery_cohort WHERE prescription_id=?",UUID.class,prescriptionId);
        UUID cohort=cohorts.isEmpty()?null:cohorts.getFirst();
        jdbc.update("""
                INSERT INTO PHARMACY_EVENT_OUTBOX(event_id, routing_key, aggregate_id, payload, created_at,
                    attempts, available_at, delivery_enabled, care_contract_version, care_lifecycle_order,care_delivery_cohort_id)
                VALUES (?, ?, ?, ?, clock_timestamp(), 0, clock_timestamp(), ?, 1, ?,?)
                ON CONFLICT DO NOTHING
                """, event.eventId(), event.eventType().routingKey(), prescriptionId, payload,cohort!=null, order,cohort);
        // Check both identity and semantic uniqueness. Never overwrite bytes/ID on re-delivery or collision.
        var rows = jdbc.query("""
                SELECT event_id, payload, delivery_enabled, care_contract_version, care_delivery_cohort_id FROM PHARMACY_EVENT_OUTBOX
                WHERE aggregate_id = ? AND care_contract_version = 1 AND care_lifecycle_order = ?
                """, (rs, row) -> new Stored(rs.getObject("event_id", UUID.class), rs.getString("payload"),
                rs.getBoolean("delivery_enabled"), rs.getShort("care_contract_version"),rs.getObject("care_delivery_cohort_id",UUID.class)), prescriptionId, order);
        if (rows.size() != 1 || !rows.get(0).eventId().equals(event.eventId()) || !rows.get(0).payload().equals(payload)
                || rows.get(0).enabled()!=(cohort!=null) || rows.get(0).version() != 1) {
            throw new PrescriptionRuleException("PHARMACY_CARE_EVENT_CONFLICT", "Immutable V1 lifecycle event conflict");
        }
    }

    private int eventOrder(PrescriptionCareEvent event) {
        if (event.eventType() == EventType.CREATED) return 0;
        if (event.eventType() == EventType.REVISED) {
            if (event.payload().revision() == null || event.payload().revision() < 1) {
                throw new PrescriptionRuleException("PHARMACY_CARE_REVISION_INVALID", "Revision event requires a positive revision");
            }
            return event.payload().revision();
        }
        // Terminal events follow the latest applied revision; an unrevised (or not yet persisted) aggregate is revision 0.
        var revisions = jdbc.queryForList(
                "SELECT revision FROM prescription WHERE prescription_id = ? AND care_contract_version = 1",
                Integer.class, event.payload().prescriptionId());
        Integer revision = revisions == null || revisions.isEmpty() ? Integer.valueOf(0) : revisions.getFirst();
        if (revision == null || revision < 0) {
            throw new PrescriptionRuleException("PHARMACY_CARE_REVISION_INVALID", "Persisted prescription revision is required");
        }
        return Math.addExact(revision, 1);
    }

    private record Stored(UUID eventId, String payload, boolean enabled, short version,UUID cohort) { }
}
