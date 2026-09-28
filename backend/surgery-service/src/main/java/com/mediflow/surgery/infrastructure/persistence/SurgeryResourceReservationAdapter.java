package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryScheduleConflictException;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** All writers take case row, sorted resource mutexes, then reservation rows. */
@Repository
@Profile("!test")
public class SurgeryResourceReservationAdapter implements SurgeryResourceReservationPort {

    private static final Comparator<ResourceKey> RESOURCE_ORDER =
            Comparator.comparing(ResourceKey::kind).thenComparing(key -> key.id().toString());

    private final SurgeryCaseJpaRepository cases;
    private final JdbcTemplate jdbc;

    public SurgeryResourceReservationAdapter(SurgeryCaseJpaRepository cases, JdbcTemplate jdbc) {
        this.cases = cases;
        this.jdbc = jdbc;
    }

    @Override
    public void reserve(SurgerySchedule schedule, Instant at) {
        requireTransaction();
        if (schedule == null || at == null) throw new IllegalArgumentException("Schedule/time bắt buộc");
        SurgeryCaseJpaEntity surgeryCase = lockCase(schedule.surgeryCaseId());
        if (surgeryCase.status != SurgeryStatus.READY) {
            throw new SurgeryScheduleConflictException();
        }
        List<DraftRow> drafts = jdbc.query("""
                SELECT surgery_case_id, revision, room_id, starts_at, ends_at, status
                FROM surgery_schedule WHERE schedule_id = ?
                """, (rs, ignored) -> new DraftRow(
                rs.getObject("surgery_case_id", UUID.class), rs.getLong("revision"),
                rs.getObject("room_id", UUID.class),
                rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                rs.getString("status")), schedule.scheduleId());
        if (drafts.size() != 1 || !drafts.getFirst().matches(schedule)) {
            throw new SurgeryScheduleConflictException();
        }
        List<SurgeryTeamAssignment> storedTeam = jdbc.query("""
                SELECT staff_id, role FROM surgery_team_assignment WHERE schedule_id = ?
                """, (rs, ignored) -> new SurgeryTeamAssignment(
                rs.getObject("staff_id", UUID.class),
                SurgeryTeamRole.valueOf(rs.getString("role"))), schedule.scheduleId());
        if (!new HashSet<>(storedTeam).equals(new HashSet<>(schedule.teamAssignments()))) {
            throw new SurgeryScheduleConflictException();
        }
        List<ResourceKey> oldResources = activeResources(schedule.surgeryCaseId());
        List<ResourceKey> newResources = keys(schedule);
        lockResources(union(oldResources, newResources));
        for (ResourceKey key : newResources) {
            Integer conflicts = jdbc.queryForObject("""
                    SELECT count(*) FROM surgery_resource_reservation
                    WHERE resource_type = ? AND resource_id = ?
                      AND surgery_case_id <> ?
                      AND (status = 'IN_USE' OR
                           (status = 'RESERVED' AND starts_at < ? AND ends_at > ?))
                    """, Integer.class, key.kind(), key.id(), schedule.surgeryCaseId(),
                    Timestamp.from(schedule.endsAt()), Timestamp.from(schedule.startsAt()));
            if (conflicts != null && conflicts > 0) throw new SurgeryScheduleConflictException();
        }
        jdbc.update("""
                UPDATE surgery_resource_reservation
                SET status = 'RELEASED', updated_at = ?
                WHERE surgery_case_id = ? AND status IN ('RESERVED', 'IN_USE')
                """, Timestamp.from(at), schedule.surgeryCaseId());
        for (ResourceKey key : newResources) {
            jdbc.update("""
                    INSERT INTO surgery_resource_reservation
                    (reservation_id, surgery_case_id, schedule_id, schedule_revision, resource_type,
                     resource_id, starts_at, ends_at, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RESERVED')
                    """, UUID.randomUUID(), schedule.surgeryCaseId(), schedule.scheduleId(),
                    schedule.revision(), key.kind(), key.id(), Timestamp.from(schedule.startsAt()),
                    Timestamp.from(schedule.endsAt()));
        }
        int finalized = jdbc.update("""
                UPDATE surgery_schedule SET status = 'FINALIZED', updated_at = ?
                WHERE schedule_id = ? AND revision = ? AND status = 'DRAFT'
                """, Timestamp.from(at), schedule.scheduleId(), schedule.revision());
        if (finalized != 1) throw new SurgeryScheduleConflictException();
    }

    @Override
    public void markInUse(UUID caseId, UUID scheduleId, long scheduleRevision, Instant at) {
        requireTransaction();
        validateIdentity(scheduleId, scheduleRevision, at);
        SurgeryCaseJpaEntity surgeryCase = lockCase(caseId);
        if (surgeryCase.status != SurgeryStatus.SCHEDULED) {
            throw new SurgeryScheduleConflictException();
        }
        requireSchedule(caseId, scheduleId, scheduleRevision, "FINALIZED");
        List<ResourceKey> reserved = activeResources(caseId, scheduleId, scheduleRevision);
        lockResources(reserved);
        Integer teamCount = jdbc.queryForObject("""
                SELECT count(*) FROM surgery_team_assignment WHERE schedule_id = ?
                """, Integer.class, scheduleId);
        if (reserved.size() != (teamCount == null ? 0 : teamCount) + 1) {
            throw new SurgeryScheduleConflictException();
        }
        int changed = jdbc.update("""
                UPDATE surgery_resource_reservation
                SET status = 'IN_USE', updated_at = ?
                WHERE surgery_case_id = ? AND schedule_id = ? AND schedule_revision = ?
                  AND status = 'RESERVED'
                """, Timestamp.from(at), caseId, scheduleId, scheduleRevision);
        if (changed != reserved.size()) throw new SurgeryScheduleConflictException();
        int started = jdbc.update("""
                UPDATE surgery_schedule SET status = 'IN_USE', updated_at = ?
                WHERE surgery_case_id = ? AND schedule_id = ? AND revision = ?
                  AND status = 'FINALIZED'
                """, Timestamp.from(at), caseId, scheduleId, scheduleRevision);
        if (started != 1) throw new SurgeryScheduleConflictException();
    }

    @Override
    public void release(UUID caseId, UUID scheduleId, long scheduleRevision, Instant at) {
        requireTransaction();
        validateIdentity(scheduleId, scheduleRevision, at);
        lockCase(caseId);
        requireSchedule(caseId, scheduleId, scheduleRevision, null);
        lockResources(activeResources(caseId, scheduleId, scheduleRevision));
        jdbc.update("""
                UPDATE surgery_resource_reservation
                SET status = 'RELEASED', updated_at = ?
                WHERE surgery_case_id = ? AND schedule_id = ? AND schedule_revision = ?
                  AND status IN ('RESERVED', 'IN_USE')
                """, Timestamp.from(at), caseId, scheduleId, scheduleRevision);
        jdbc.update("""
                UPDATE surgery_schedule SET status = 'RELEASED', updated_at = ?
                WHERE surgery_case_id = ? AND schedule_id = ? AND revision = ?
                  AND status IN ('FINALIZED', 'IN_USE')
                """, Timestamp.from(at), caseId, scheduleId, scheduleRevision);
    }

    private SurgeryCaseJpaEntity lockCase(UUID caseId) {
        if (caseId == null) throw new IllegalArgumentException("Case ID bắt buộc");
        return cases.lockById(caseId).orElseThrow(SurgeryScheduleConflictException::new);
    }

    private List<ResourceKey> activeResources(UUID caseId) {
        return jdbc.query("""
                SELECT resource_type, resource_id
                FROM surgery_resource_reservation
                WHERE surgery_case_id = ? AND status IN ('RESERVED', 'IN_USE')
                """, (rs, ignored) -> new ResourceKey(
                rs.getString("resource_type"), rs.getObject("resource_id", UUID.class)), caseId);
    }

    private List<ResourceKey> activeResources(UUID caseId, UUID scheduleId, long scheduleRevision) {
        return jdbc.query("""
                SELECT resource_type, resource_id
                FROM surgery_resource_reservation
                WHERE surgery_case_id = ? AND schedule_id = ? AND schedule_revision = ?
                  AND status IN ('RESERVED', 'IN_USE')
                """, (rs, ignored) -> new ResourceKey(
                rs.getString("resource_type"), rs.getObject("resource_id", UUID.class)),
                caseId, scheduleId, scheduleRevision);
    }

    private void requireSchedule(UUID caseId, UUID scheduleId, long revision, String requiredStatus) {
        Integer count = requiredStatus == null
                ? jdbc.queryForObject("""
                    SELECT count(*) FROM surgery_schedule
                    WHERE surgery_case_id = ? AND schedule_id = ? AND revision = ?
                    """, Integer.class, caseId, scheduleId, revision)
                : jdbc.queryForObject("""
                    SELECT count(*) FROM surgery_schedule
                    WHERE surgery_case_id = ? AND schedule_id = ? AND revision = ?
                      AND status = ?
                    """, Integer.class, caseId, scheduleId, revision, requiredStatus);
        if (count == null || count != 1) throw new SurgeryScheduleConflictException();
    }

    private static void validateIdentity(UUID scheduleId, long revision, Instant at) {
        if (scheduleId == null || revision < 1 || at == null) {
            throw new IllegalArgumentException("Schedule identity/revision/time bắt buộc");
        }
    }

    private List<ResourceKey> keys(SurgerySchedule schedule) {
        List<ResourceKey> resources = new ArrayList<>();
        resources.add(new ResourceKey("ROOM", schedule.roomId()));
        schedule.teamAssignments().forEach(person ->
                resources.add(new ResourceKey("STAFF", person.staffId())));
        return resources;
    }

    private List<ResourceKey> union(List<ResourceKey> oldKeys, List<ResourceKey> newKeys) {
        Set<ResourceKey> unique = new HashSet<>(oldKeys);
        unique.addAll(newKeys);
        return unique.stream().sorted(RESOURCE_ORDER).toList();
    }

    private void lockResources(List<ResourceKey> resourceKeys) {
        for (ResourceKey key : resourceKeys.stream().distinct().sorted(RESOURCE_ORDER).toList()) {
            jdbc.update("""
                    INSERT INTO surgery_resource_mutex (resource_type, resource_id)
                    VALUES (?, ?) ON CONFLICT (resource_type, resource_id) DO NOTHING
                    """, key.kind(), key.id());
            jdbc.queryForObject("""
                    SELECT resource_id FROM surgery_resource_mutex
                    WHERE resource_type = ? AND resource_id = ? FOR UPDATE
                    """, UUID.class, key.kind(), key.id());
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Resource reservation yêu cầu application transaction");
        }
    }

    private record ResourceKey(String kind, UUID id) {
    }

    private record DraftRow(UUID caseId, long revision, UUID roomId,
                            Instant start, Instant end, String status) {
        boolean matches(SurgerySchedule schedule) {
            return caseId.equals(schedule.surgeryCaseId()) && revision == schedule.revision()
                    && roomId.equals(schedule.roomId())
                    && start.equals(schedule.startsAt().truncatedTo(ChronoUnit.MICROS))
                    && end.equals(schedule.endsAt().truncatedTo(ChronoUnit.MICROS))
                    && "DRAFT".equals(status);
        }
    }
}
