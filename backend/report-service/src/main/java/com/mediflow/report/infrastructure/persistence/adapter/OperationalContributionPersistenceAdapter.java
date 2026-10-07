package com.mediflow.report.infrastructure.persistence.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.port.out.OperationalContributionStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.OperationalContribution;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class OperationalContributionPersistenceAdapter implements OperationalContributionStorePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final OperationalSnapshotCodec snapshots;

    public OperationalContributionPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.snapshots = new OperationalSnapshotCodec(mapper);
    }

    @Override
    public void recordEvent(ApplyOperationalContributionCommand command) {
        var event = command.event();
        var metadata = event.metadata();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", metadata.eventId().toString());
        envelope.put("eventType", metadata.eventType());
        envelope.put("version", metadata.version());
        envelope.put("occurredAt", metadata.occurredAt().toString());
        envelope.put("correlationId", metadata.correlationId());
        envelope.put("producer", metadata.producer());
        envelope.put("payload", event.payload());
        String json = json(envelope);
        String fingerprint = hash(json);
        // Keep only metadata/business identities and accepted aggregate inputs. Do NOT retain
        // diagnosis, lab results, free-text conclusion or the entire producer payload in Report.
        envelope.remove("payload");
        envelope.put("sourceField", metadata.sourceField());
        envelope.put("sourceId", metadata.sourceId().toString());
        String eventSnapshot = json(envelope);
        String contributionSnapshot = snapshots.snapshot(command.contributions());
        String projectionFingerprint = hash(contributionSnapshot);
        jdbc.update("""
                INSERT INTO operational_event_journal(event_id, event_type, event_snapshot,
                    contribution_snapshot, envelope_fingerprint, projection_fingerprint)
                VALUES (?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?, ?) ON CONFLICT (event_id) DO NOTHING
                """, metadata.eventId(), metadata.eventType(), eventSnapshot, contributionSnapshot, fingerprint, projectionFingerprint);
        requireSame(fingerprint, jdbc.queryForObject(
                "SELECT envelope_fingerprint FROM operational_event_journal WHERE event_id = ?",
                String.class, metadata.eventId()), "OPERATIONAL_EVENT_CONFLICT");
        requireSame(projectionFingerprint, jdbc.queryForObject(
                "SELECT projection_fingerprint FROM operational_event_journal WHERE event_id = ?",
                String.class, metadata.eventId()), "OPERATIONAL_PROJECTION_CONFLICT");
        var fact = command.contributions().getFirst();
        String payloadFingerprint = hash(json(event.payload()));
        jdbc.update("""
                INSERT INTO operational_source_snapshot(source_type,source_id,source_revision,event_type,
                    first_event_id,payload_fingerprint,evidence_state)
                VALUES (?,?,?,?,?,?,'VERIFIED_PAYLOAD') ON CONFLICT DO NOTHING
                """, fact.sourceType(),fact.sourceId(),fact.sourceRevision(),metadata.eventType(),
                metadata.eventId(),payloadFingerprint);
        String storedPayload = jdbc.queryForObject("""
                SELECT payload_fingerprint FROM operational_source_snapshot
                WHERE source_type=? AND source_id=? AND source_revision=? AND event_type=?
                """, String.class, fact.sourceType(),fact.sourceId(),fact.sourceRevision(),metadata.eventType());
        if (storedPayload == null) throw new ReportRuleException("OPERATIONAL_LEGACY_SOURCE_UNVERIFIED",
                "Legacy source payload requires controlled revalidation before accepting a new delivery");
        requireSame(payloadFingerprint, storedPayload, "OPERATIONAL_SOURCE_PAYLOAD_CONFLICT");
    }

    @Override
    public boolean claimDelivery(OperationalContribution fact) {
        String fingerprint = fingerprint(fact);
        int inserted = jdbc.update("""
                INSERT INTO operational_delivery(event_id, metric_type, fact_fingerprint) VALUES (?, ?, ?)
                ON CONFLICT (event_id, metric_type) DO NOTHING
                """, fact.eventId(), fact.metric().name(), fingerprint);
        requireSame(fingerprint, jdbc.queryForObject("""
                SELECT fact_fingerprint FROM operational_delivery WHERE event_id = ? AND metric_type = ?
                """, String.class, fact.eventId(), fact.metric().name()), "OPERATIONAL_DELIVERY_CONFLICT");
        return inserted == 1;
    }

    @Override
    public boolean insertContribution(OperationalContribution fact) {
        String fingerprint = fingerprint(fact);
        int inserted = jdbc.update("""
                INSERT INTO operational_contribution(contribution_id, event_id, source_type, source_id,
                    source_revision, metric_type, department_id, care_episode_type, care_episode_id,
                    metric_date, numeric_value, category, occurred_at, fact_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_type, source_id, source_revision, metric_type) DO NOTHING
                """, UUID.randomUUID(), fact.eventId(), fact.sourceType(), fact.sourceId(), fact.sourceRevision(),
                fact.metric().name(), fact.departmentId(), fact.careEpisodeType(), fact.careEpisodeId(),
                Date.valueOf(fact.metricDate()), fact.value(), fact.category(), Timestamp.from(fact.occurredAt()), fingerprint);
        requireSame(fingerprint, jdbc.queryForObject("""
                SELECT fact_fingerprint FROM operational_contribution
                WHERE source_type = ? AND source_id = ? AND source_revision = ? AND metric_type = ?
                """, String.class, fact.sourceType(), fact.sourceId(), fact.sourceRevision(),
                fact.metric().name()), "OPERATIONAL_SOURCE_CONFLICT");
        return inserted == 1;
    }

    @Override
    public void incrementScope(OperationalContribution fact, UUID departmentId) {
        // Column names come only from the closed enum, never from input strings.
        String column = switch (fact.metric()) {
            case COMPLETED_VISITS -> "completed_visits";
            case ADMISSIONS -> "admissions";
            case DISCHARGES -> "discharges";
            case LAB_TESTS -> "lab_tests";
            case DISPENSED_PRESCRIPTIONS -> "dispensed_prescriptions";
            case DISPENSED_UNITS -> "dispensed_units";
            case SURGERIES_COMPLETED -> "surgeries_completed";
            case SURGERIES_CANCELLED -> "surgeries_cancelled";
            case SURGERY_DURATION_MINUTES -> "surgery_duration_minutes";
        };
        jdbc.update("INSERT INTO daily_operational_report(report_id, report_date, department_id, " + column
                + ") VALUES (?, ?, ?, ?) ON CONFLICT (report_date, department_id) DO UPDATE SET "
                + column + " = daily_operational_report." + column + " + EXCLUDED." + column
                + ", updated_at = now()", UUID.randomUUID(), Date.valueOf(fact.metricDate()), departmentId,
                fact.value().longValueExact());
    }

    private String fingerprint(OperationalContribution fact) {
        // Excludes delivery eventId; occurredAt here is the explicit source business timestamp,
        // not a republished envelope timestamp. Nanoseconds survive DB timestamp rounding via the hash.
        return snapshots.fingerprint(fact);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot journal operational event", exception);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void requireSame(String expected, String actual, String code) {
        if (!expected.equals(actual)) {
            throw new ReportRuleException(code, "Operational source/delivery payload conflict");
        }
    }
}
