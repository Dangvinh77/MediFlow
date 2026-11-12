package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.RecordAmendmentRepositoryPort;
import com.mediflow.clinical.domain.model.AmendmentAssignment;
import com.mediflow.clinical.domain.model.AmendmentSection;
import com.mediflow.clinical.domain.model.RecordAmendment;
import com.mediflow.clinical.domain.model.RecordAmendmentStatus;

@Component
public class JdbcRecordAmendmentAdapter implements RecordAmendmentRepositoryPort {
    private static final String COLUMNS = "a.amendment_id, a.record_id, a.section, a.original_text, a.amended_text, a.reason, "
            + "a.author_staff_id, a.assigned_by_staff_id, a.status, a.requested_at, a.decided_by_staff_id, a.decision_note, "
            + "a.decided_at, a.content_hash, a.effective_revision";
    private final JdbcTemplate jdbc;

    public JdbcRecordAmendmentAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(RecordAmendment a) {
        Long effectiveRevision = a.getStatus() == RecordAmendmentStatus.APPLIED
                ? nextEffectiveRevision(a.getRecordId()) : null;
        jdbc.update("""
                INSERT INTO record_amendment(amendment_id, record_id, section, original_text, amended_text, reason,
                    author_staff_id, assigned_by_staff_id, status, requested_at, decided_by_staff_id, decision_note,
                    decided_at, content_hash, effective_revision) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, a.getAmendmentId(), a.getRecordId(), a.getSection().name(), a.getOriginalText(), a.getAmendedText(),
                a.getReason(), a.getAuthorStaffId(), a.getAssignedByStaffId(), a.getStatus().name(),
                Timestamp.from(a.getRequestedAt()), a.getDecidedByStaffId(), a.getDecisionNote(),
                a.getDecidedAt() == null ? null : Timestamp.from(a.getDecidedAt()), a.getContentHash(), effectiveRevision);
    }

    @Override
    public Optional<RecordAmendment> findByIdForUpdate(UUID amendmentId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM record_amendment a WHERE a.amendment_id = ? FOR UPDATE",
                JdbcRecordAmendmentAdapter::row, amendmentId).stream().findFirst();
    }

    @Override
    public void decide(RecordAmendment a) {
        Long effectiveRevision = a.getStatus() == RecordAmendmentStatus.APPROVED
                ? nextEffectiveRevision(a.getRecordId()) : null;
        jdbc.update("""
                UPDATE record_amendment SET status = ?, decided_by_staff_id = ?, decision_note = ?, decided_at = ?,
                    content_hash = ?, effective_revision = ? WHERE amendment_id = ? AND status = 'PENDING'
                """, a.getStatus().name(), a.getDecidedByStaffId(), a.getDecisionNote(), Timestamp.from(a.getDecidedAt()),
                a.getContentHash(), effectiveRevision, a.getAmendmentId());
    }

    @Override
    public List<RecordAmendment> findByRecord(UUID recordId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM record_amendment a WHERE a.record_id = ? ORDER BY a.requested_at, a.amendment_id",
                JdbcRecordAmendmentAdapter::row, recordId);
    }

    @Override
    public List<PendingAmendment> findPending() {
        return jdbc.query("SELECT " + COLUMNS + ", r.department_id FROM record_amendment a "
                        + "JOIN medical_record r ON r.record_id = a.record_id WHERE a.status = 'PENDING' "
                        + "ORDER BY a.requested_at, a.amendment_id",
                (rs, n) -> new PendingAmendment(row(rs, n), rs.getObject("department_id", UUID.class)));
    }

    @Override
    public void insertAssignment(AmendmentAssignment s) {
        jdbc.update("""
                INSERT INTO record_amendment_assignment(assignment_id, record_id, assignee_staff_id, assigned_by_staff_id,
                    reason, created_at) VALUES (?,?,?,?,?,?)
                """, s.getAssignmentId(), s.getRecordId(), s.getAssigneeStaffId(), s.getAssignedByStaffId(), s.getReason(),
                Timestamp.from(s.getCreatedAt()));
    }

    @Override
    public Optional<AmendmentAssignment> findAssignment(UUID recordId, UUID assigneeStaffId) {
        return jdbc.query("""
                SELECT assignment_id, record_id, assignee_staff_id, assigned_by_staff_id, reason, created_at
                FROM record_amendment_assignment WHERE record_id = ? AND assignee_staff_id = ? AND revoked_at IS NULL
                ORDER BY created_at DESC, assignment_id LIMIT 1
                """, (rs, n) -> AmendmentAssignment.restore(rs.getObject("assignment_id", UUID.class),
                rs.getObject("record_id", UUID.class), rs.getObject("assignee_staff_id", UUID.class),
                rs.getObject("assigned_by_staff_id", UUID.class), rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant()), recordId, assigneeStaffId).stream().findFirst();
    }

    @Override
    public boolean assignmentExists(UUID recordId, UUID assignmentId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM record_amendment_assignment WHERE record_id = ? AND assignment_id = ?",
                Integer.class, recordId, assignmentId);
        return count != null && count > 0;
    }

    @Override
    public void revokeAssignment(UUID recordId, UUID assignmentId, UUID revokedByStaffId, java.time.Instant at) {
        jdbc.update("""
                UPDATE record_amendment_assignment SET revoked_at = ?, revoked_by_staff_id = ?
                WHERE record_id = ? AND assignment_id = ? AND revoked_at IS NULL
                """, Timestamp.from(at), revokedByStaffId, recordId, assignmentId);
    }

    private static RecordAmendment row(ResultSet rs, int n) throws SQLException {
        return RecordAmendment.restore(rs.getObject("amendment_id", UUID.class), rs.getObject("record_id", UUID.class),
                AmendmentSection.valueOf(rs.getString("section")), rs.getString("original_text"),
                rs.getString("amended_text"), rs.getString("reason"), rs.getObject("author_staff_id", UUID.class),
                rs.getObject("assigned_by_staff_id", UUID.class), RecordAmendmentStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("requested_at").toInstant(), rs.getObject("decided_by_staff_id", UUID.class),
                rs.getString("decision_note"), instant(rs, "decided_at"), rs.getString("content_hash"),
                (Long) rs.getObject("effective_revision"));
    }

    private long nextEffectiveRevision(UUID recordId) {
        Long next = jdbc.queryForObject("SELECT COALESCE(MAX(effective_revision), 0) + 1 "
                + "FROM record_amendment WHERE record_id = ?", Long.class, recordId);
        return next == null ? 1L : next;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
