package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase.Candidate;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityInvalidationPort;
import com.mediflow.surgery.domain.model.SurgeryAuthorityChange;
import com.mediflow.surgery.domain.model.SurgeryAuthorityChange.ReferenceKind;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@Profile("!test")
public class SurgeryAuthorityInvalidationAdapter implements SurgeryAuthorityInvalidationPort {
    private final JdbcTemplate jdbc;
    public SurgeryAuthorityInvalidationAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Capture capture(UUID eventId, SurgeryAuthorityChange change, String correlationId, Instant now) {
        requireTransaction();
        if (eventId == null || change == null || correlationId == null || now == null) throw new IllegalArgumentException("Authority evidence required");
        var inserted = jdbc.query("""
                INSERT INTO surgery_authority_change (event_id, reference_key, reference_kind, reference_id, team_role,
                    source_revision, occurred_at, actor_account_id, reason, correlation_id, fingerprint)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (reference_key, source_revision) DO NOTHING RETURNING event_id
                """, (rs, ignored) -> rs.getObject(1, UUID.class), eventId, change.referenceKey(), change.referenceKind().name(),
                change.referenceId(), change.teamRole() == null ? null : change.teamRole().name(), change.revision(),
                Timestamp.from(change.occurredAt()), change.actorAccountId(), change.reason(), correlationId, change.fingerprint());
        if (inserted.isEmpty()) {
            var existing = jdbc.queryForObject("SELECT fingerprint FROM surgery_authority_change WHERE reference_key = ? AND source_revision = ?",
                    String.class, change.referenceKey(), change.revision());
            return change.fingerprint().equals(existing) ? Capture.MATCHING : Capture.CONFLICT;
        }
        jdbc.update("""
                INSERT INTO surgery_authority_invalidation (event_id, surgery_case_id, readiness_snapshot_id,
                    schedule_id, schedule_revision, status, next_attempt_at, updated_at)
                SELECT ?, c.surgery_case_id, c.readiness_snapshot_id, s.schedule_id, s.revision, 'PENDING', ?, ?
                FROM surgery_case c JOIN surgery_schedule s ON s.surgery_case_id = c.surgery_case_id
                WHERE c.status IN ('READY', 'SCHEDULED') AND c.readiness_snapshot_id IS NOT NULL
                    AND ((? = 'ROOM' AND s.room_id = ?) OR (? = 'STAFF_CAPABILITY' AND EXISTS (
                        SELECT 1 FROM surgery_team_assignment a WHERE a.schedule_id = s.schedule_id AND a.staff_id = ? AND a.role = ?)))
                """, eventId, Timestamp.from(now), Timestamp.from(now), change.referenceKind().name(), change.referenceId(),
                change.referenceKind().name(), change.referenceId(), change.teamRole() == null ? null : change.teamRole().name());
        return Capture.CREATED;
    }

    @Override public List<Candidate> findDue(Instant now, int limit) {
        if (now == null || limit < 1 || limit > 100) throw new IllegalArgumentException("Bounded authority query required");
        return jdbc.query("""
                SELECT event_id, surgery_case_id, readiness_snapshot_id, schedule_id, schedule_revision
                FROM surgery_authority_invalidation WHERE status = 'PENDING' AND next_attempt_at <= ?
                ORDER BY next_attempt_at, surgery_case_id, event_id LIMIT ?
                """, (rs, ignored) -> new Candidate(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getLong(5)), Timestamp.from(now), limit);
    }

    @Override public Optional<Work> lockPending(Candidate candidate, Instant now) {
        requireTransaction();
        if (candidate == null || now == null) throw new IllegalArgumentException("Authority work identity required");
        var found = jdbc.query("""
                SELECT a.* FROM surgery_authority_invalidation j JOIN surgery_authority_change a ON a.event_id = j.event_id
                WHERE j.event_id = ? AND j.surgery_case_id = ? AND j.readiness_snapshot_id = ?
                    AND j.schedule_id = ? AND j.schedule_revision = ? AND j.status = 'PENDING' AND j.next_attempt_at <= ?
                FOR UPDATE OF j
                """, (rs, ignored) -> new Work(change(rs), rs.getString("correlation_id")), candidate.eventId(), candidate.surgeryCaseId(),
                candidate.readinessSnapshotId(), candidate.scheduleId(), candidate.scheduleRevision(), Timestamp.from(now));
        return found.stream().findFirst();
    }

    @Override public void finish(Candidate candidate, Completion completion, Instant now) {
        requireTransaction();
        if (candidate == null || completion == null || now == null) throw new IllegalArgumentException("Authority completion required");
        int changed = jdbc.update("""
                UPDATE surgery_authority_invalidation SET status = ?, failure_code = NULL, updated_at = ?
                WHERE event_id = ? AND surgery_case_id = ? AND readiness_snapshot_id = ?
                    AND schedule_id = ? AND schedule_revision = ? AND status = 'PENDING'
                """, completion.name(), Timestamp.from(now), candidate.eventId(), candidate.surgeryCaseId(),
                candidate.readinessSnapshotId(), candidate.scheduleId(), candidate.scheduleRevision());
        if (changed != 1) throw new IllegalStateException("Authority completion lost its work fence");
    }

    @Override public void defer(Candidate candidate, String failureCode, Instant now) {
        requireTransaction();
        if (candidate == null || now == null || failureCode == null || !failureCode.matches("[A-Za-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Safe authority retry required");
        }
        jdbc.update("""
                UPDATE surgery_authority_invalidation SET attempt_count = LEAST(1000000, attempt_count + 1),
                    next_attempt_at = CAST(? AS timestamptz) + make_interval(secs => LEAST(300, 5 * power(2, LEAST(6, attempt_count)))),
                    failure_code = ?, updated_at = ?
                WHERE event_id = ? AND surgery_case_id = ? AND readiness_snapshot_id = ?
                    AND schedule_id = ? AND schedule_revision = ? AND status = 'PENDING'
                """, Timestamp.from(now), failureCode, Timestamp.from(now), candidate.eventId(), candidate.surgeryCaseId(),
                candidate.readinessSnapshotId(), candidate.scheduleId(), candidate.scheduleRevision());
    }

    private static SurgeryAuthorityChange change(ResultSet rs) throws SQLException {
        String role = rs.getString("team_role");
        return new SurgeryAuthorityChange(ReferenceKind.valueOf(rs.getString("reference_kind")), rs.getObject("reference_id", UUID.class),
                role == null ? null : SurgeryTeamRole.valueOf(role), rs.getLong("source_revision"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_account_id", UUID.class), rs.getString("reason"), rs.getString("fingerprint"));
    }
    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Authority mutation needs a transaction");
    }
}
