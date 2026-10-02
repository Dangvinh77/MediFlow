package com.mediflow.report.infrastructure.persistence.adapter;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.dto.response.OperationalReplayProgress;
import com.mediflow.report.application.dto.response.OperationalReplayProgress.Status;
import com.mediflow.report.application.port.out.OperationalReplayStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.OperationalContribution;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class OperationalReplayPersistenceAdapter implements OperationalReplayStorePort {
    private final JdbcTemplate jdbc;
    private final OperationalSnapshotCodec snapshots;

    public OperationalReplayPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.snapshots = new OperationalSnapshotCodec(mapper);
    }

    @Override
    public OperationalReplayProgress freeze(UUID generationId) {
        jdbc.update("INSERT INTO operational_replay_generation(generation_id, status) VALUES (?, 'BUILDING')", generationId);
        // INSERT SELECT freezes the committed set visible to this statement. Neither now() nor MAX(id)
        // is a safe cut-off: an older transaction can commit after either of those watermarks.
        int count = jdbc.update("""
                INSERT INTO operational_replay_input(generation_id, event_id, event_snapshot, contribution_snapshot,
                    envelope_fingerprint, projection_fingerprint, projector_version)
                SELECT ?, event_id, event_snapshot, contribution_snapshot, envelope_fingerprint,
                    projection_fingerprint, projector_version FROM operational_event_journal
                """, generationId);
        jdbc.update("UPDATE operational_replay_generation SET source_events = ? WHERE generation_id = ?", count, generationId);
        return progress(generationId);
    }

    @Override
    public OperationalReplayProgress lock(UUID generationId) { return readProgress(generationId, " FOR UPDATE"); }

    @Override
    public OperationalReplayProgress progress(UUID generationId) { return readProgress(generationId, ""); }

    private OperationalReplayProgress readProgress(UUID id, String lock) {
        var rows = jdbc.query("""
                SELECT generation_id, status, source_events, applied_events FROM operational_replay_generation
                WHERE generation_id = ?
                """ + lock, (rs, row) -> new OperationalReplayProgress(rs.getObject("generation_id", UUID.class),
                Status.valueOf(rs.getString("status")), rs.getLong("source_events"), rs.getLong("applied_events")), id);
        if (rows.isEmpty()) throw new ReportRuleException("OPERATIONAL_REPLAY_NOT_FOUND", "Replay generation not found");
        return rows.get(0);
    }

    @Override
    public List<ApplyOperationalContributionCommand> pending(UUID generationId, int limit) {
        return jdbc.query("""
                SELECT event_id, event_snapshot::text, contribution_snapshot::text, projection_fingerprint, projector_version
                FROM operational_replay_input WHERE generation_id = ? AND NOT applied ORDER BY event_id LIMIT ?
                """, (rs, row) -> snapshots.decode(rs.getObject("event_id", UUID.class), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getInt(5)), generationId, limit);
    }

    @Override
    public boolean insertContribution(UUID generationId, OperationalContribution fact) {
        String fingerprint = snapshots.fingerprint(fact);
        int inserted = jdbc.update("""
                INSERT INTO operational_replay_contribution(generation_id, source_type, source_id, source_revision,
                    metric_type, fact_fingerprint, fact_snapshot) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
                ON CONFLICT (generation_id, source_type, source_id, source_revision, metric_type) DO NOTHING
                """, generationId, fact.sourceType(), fact.sourceId(), fact.sourceRevision(), fact.metric().name(),
                fingerprint, snapshots.json(snapshots.factFields(fact)));
        String stored = jdbc.queryForObject("""
                SELECT fact_fingerprint FROM operational_replay_contribution WHERE generation_id = ?
                AND source_type = ? AND source_id = ? AND source_revision = ? AND metric_type = ?
                """, String.class, generationId, fact.sourceType(), fact.sourceId(), fact.sourceRevision(), fact.metric().name());
        if (!fingerprint.equals(stored)) {
            throw new ReportRuleException("OPERATIONAL_REPLAY_SOURCE_CONFLICT", "Replay semantic source conflict");
        }
        return inserted == 1;
    }

    @Override
    public void incrementScope(UUID generationId, OperationalContribution fact, UUID departmentId) {
        jdbc.update("""
                INSERT INTO operational_replay_scope(generation_id, metric_date, department_id, metric_type, numeric_value)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (generation_id, metric_date, department_id, metric_type)
                DO UPDATE SET numeric_value = operational_replay_scope.numeric_value + EXCLUDED.numeric_value
                """, generationId, Date.valueOf(fact.metricDate()), departmentId, fact.metric().name(), fact.value().longValueExact());
    }

    @Override
    public void markApplied(UUID generationId, UUID eventId) {
        if (jdbc.update("UPDATE operational_replay_input SET applied = true WHERE generation_id = ? AND event_id = ? AND NOT applied",
                generationId, eventId) == 1) {
            jdbc.update("UPDATE operational_replay_generation SET applied_events = applied_events + 1 WHERE generation_id = ?",
                    generationId);
        }
    }

    @Override
    public OperationalReplayProgress reconcile(UUID generationId) {
        var current = lock(generationId);
        if (current.status() != Status.BUILDING) return current;
        // Compare against the immutable manifest, NOT the live tables (which can advance after freeze).
        boolean matched = Boolean.TRUE.equals(jdbc.queryForObject("""
                WITH expected_facts AS (
                    SELECT DISTINCT fact FROM operational_replay_input i,
                        LATERAL jsonb_array_elements(i.contribution_snapshot) expanded(fact) WHERE i.generation_id = ?
                ), actual_facts AS (
                    SELECT fact_snapshot AS fact FROM operational_replay_contribution WHERE generation_id = ?
                ), fact_diff AS (
                    (SELECT fact FROM expected_facts EXCEPT SELECT fact FROM actual_facts)
                    UNION ALL (SELECT fact FROM actual_facts EXCEPT SELECT fact FROM expected_facts)
                ), expected_scopes AS (
                    SELECT (fact->>'metricDate')::date metric_date, scope.department_id,
                        fact->>'metric' metric_type, sum((fact->>'value')::numeric)::bigint numeric_value
                    FROM expected_facts CROSS JOIN LATERAL
                        (VALUES (NULL::uuid), ((fact->>'departmentId')::uuid)) scope(department_id)
                    GROUP BY 1, 2, 3
                ), actual_scopes AS (
                    SELECT metric_date, department_id, metric_type, numeric_value FROM operational_replay_scope WHERE generation_id = ?
                ), scope_diff AS (
                    (SELECT * FROM expected_scopes EXCEPT SELECT * FROM actual_scopes)
                    UNION ALL (SELECT * FROM actual_scopes EXCEPT SELECT * FROM expected_scopes)
                ) SELECT NOT EXISTS (SELECT 1 FROM fact_diff) AND NOT EXISTS (SELECT 1 FROM scope_diff)
                    AND NOT EXISTS (SELECT 1 FROM operational_replay_input WHERE generation_id = ? AND NOT applied)
                    AND (SELECT count(*) FROM operational_replay_input WHERE generation_id = ?) = ?
                """, Boolean.class, generationId, generationId, generationId, generationId, generationId, current.sourceEvents()));
        Status status = matched && current.appliedEvents() == current.sourceEvents() ? Status.VERIFIED : Status.FAILED;
        jdbc.update("UPDATE operational_replay_generation SET status = ?, completed_at = now() WHERE generation_id = ?",
                status.name(), generationId);
        return progress(generationId);
    }
}
