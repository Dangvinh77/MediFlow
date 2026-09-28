package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class SurgerySchedulePersistenceAdapter implements SurgeryScheduleRepositoryPort {

    private final SurgeryCaseJpaRepository cases;
    private final JdbcTemplate jdbc;

    public SurgerySchedulePersistenceAdapter(SurgeryCaseJpaRepository cases, JdbcTemplate jdbc) {
        this.cases = cases;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SurgerySchedule> findByCaseId(UUID caseId) {
        List<SurgerySchedule> found = jdbc.query("""
                SELECT schedule_id, surgery_case_id, revision, room_id, starts_at, ends_at
                FROM surgery_schedule WHERE surgery_case_id = ?
                """, (rs, ignored) -> {
            UUID scheduleId = rs.getObject("schedule_id", UUID.class);
            List<SurgeryTeamAssignment> team = jdbc.query("""
                    SELECT staff_id, role FROM surgery_team_assignment
                    WHERE schedule_id = ? ORDER BY staff_id
                    """, (member, unused) -> new SurgeryTeamAssignment(
                    member.getObject("staff_id", UUID.class),
                    SurgeryTeamRole.valueOf(member.getString("role"))), scheduleId);
            return new SurgerySchedule(scheduleId,
                    rs.getObject("surgery_case_id", UUID.class), rs.getLong("revision"),
                    rs.getObject("room_id", UUID.class),
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("ends_at").toInstant(), team);
        }, caseId);
        return found.stream().findFirst();
    }

    @Override
    public Optional<SurgerySchedule> findRevision(UUID caseId, long revision) {
        if (caseId == null || revision < 1) return Optional.empty();
        List<SurgerySchedule> found = jdbc.query("""
                SELECT h.schedule_id, s.surgery_case_id, h.revision, h.room_id, h.starts_at, h.ends_at
                FROM surgery_schedule_history h
                JOIN surgery_schedule s ON s.schedule_id = h.schedule_id
                WHERE s.surgery_case_id = ? AND h.revision = ?
                """, (rs, ignored) -> {
            UUID scheduleId = rs.getObject("schedule_id", UUID.class);
            List<SurgeryTeamAssignment> team = jdbc.query("""
                    SELECT staff_id, role FROM surgery_team_assignment_history
                    WHERE schedule_id = ? AND revision = ? ORDER BY staff_id
                    """, (member, unused) -> new SurgeryTeamAssignment(
                    member.getObject("staff_id", UUID.class),
                    SurgeryTeamRole.valueOf(member.getString("role"))), scheduleId, revision);
            return new SurgerySchedule(scheduleId,
                    rs.getObject("surgery_case_id", UUID.class), rs.getLong("revision"),
                    rs.getObject("room_id", UUID.class), rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("ends_at").toInstant(), team);
        }, caseId, revision);
        return found.stream().findFirst();
    }

    @Override
    public void saveDraft(SurgerySchedule schedule, long expectedRevision, Instant at) {
        requireTransaction();
        if (schedule == null || at == null || expectedRevision < 0
                || schedule.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException("Draft revision không hợp lệ");
        }
        SurgeryCaseJpaEntity surgeryCase = cases.lockById(schedule.surgeryCaseId())
                .orElseThrow(SurgeryRevisionConflictException::new);
        if (surgeryCase.status != SurgeryStatus.PREOP_IN_PROGRESS) {
            throw new SurgeryRevisionConflictException();
        }
        if (expectedRevision == 0) {
            jdbc.update("""
                    INSERT INTO surgery_schedule
                    (schedule_id, surgery_case_id, revision, room_id,
                     starts_at, ends_at, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)
                    """, schedule.scheduleId(), schedule.surgeryCaseId(), schedule.revision(),
                    schedule.roomId(), Timestamp.from(schedule.startsAt()),
                    Timestamp.from(schedule.endsAt()), Timestamp.from(at), Timestamp.from(at));
        } else {
            int updated = jdbc.update("""
                    UPDATE surgery_schedule
                    SET revision = ?, room_id = ?, starts_at = ?, ends_at = ?, updated_at = ?
                    WHERE schedule_id = ? AND surgery_case_id = ?
                      AND revision = ? AND status = 'DRAFT'
                    """, schedule.revision(), schedule.roomId(),
                    Timestamp.from(schedule.startsAt()), Timestamp.from(schedule.endsAt()),
                    Timestamp.from(at), schedule.scheduleId(), schedule.surgeryCaseId(), expectedRevision);
            if (updated != 1) throw new SurgeryRevisionConflictException();
            jdbc.update("DELETE FROM surgery_team_assignment WHERE schedule_id = ?", schedule.scheduleId());
        }
        for (SurgeryTeamAssignment assignment : schedule.teamAssignments()) {
            jdbc.update("""
                    INSERT INTO surgery_team_assignment (schedule_id, staff_id, role)
                    VALUES (?, ?, ?)
                    """, schedule.scheduleId(), assignment.staffId(), assignment.role().name());
        }
        jdbc.update("""
                INSERT INTO surgery_schedule_history
                (schedule_id, revision, room_id, starts_at, ends_at, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, schedule.scheduleId(), schedule.revision(), schedule.roomId(),
                Timestamp.from(schedule.startsAt()), Timestamp.from(schedule.endsAt()),
                Timestamp.from(at));
        for (SurgeryTeamAssignment assignment : schedule.teamAssignments()) {
            jdbc.update("""
                    INSERT INTO surgery_team_assignment_history
                    (schedule_id, revision, staff_id, role) VALUES (?, ?, ?, ?)
                    """, schedule.scheduleId(), schedule.revision(),
                    assignment.staffId(), assignment.role().name());
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Draft schedule yêu cầu application transaction");
        }
    }
}
