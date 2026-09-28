package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Draft schedule revision. It describes a proposed slot; only the application finalizes reservations. */
public record SurgerySchedule(
        UUID scheduleId,
        UUID surgeryCaseId,
        long revision,
        UUID roomId,
        Instant startsAt,
        Instant endsAt,
        List<SurgeryTeamAssignment> teamAssignments) {

    public SurgerySchedule {
        if (scheduleId == null || surgeryCaseId == null || revision < 1 || roomId == null
                || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt) || teamAssignments == null) {
            throw invalid("SURGERY_SCHEDULE_INVALID");
        }
        if (teamAssignments.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("SURGERY_TEAM_ASSIGNMENT_INVALID");
        }
        teamAssignments = teamAssignments.stream().sorted(java.util.Comparator.comparing(
                assignment -> assignment.staffId().toString())).toList();
        Set<UUID> staffIds = new HashSet<>();
        for (SurgeryTeamAssignment assignment : teamAssignments) {
            if (!staffIds.add(assignment.staffId())) {
                throw invalid("SURGERY_SCHEDULE_DUPLICATE_STAFF");
            }
        }
    }

    public SurgerySchedule revise(UUID roomId, Instant start, Instant end,
                                  List<SurgeryTeamAssignment> team) {
        return new SurgerySchedule(scheduleId, surgeryCaseId, revision + 1,
                roomId, start, end, team);
    }

    /** Half-open intervals allow one case to start exactly when the preceding slot ends. */
    public boolean overlaps(SurgerySchedule other) {
        return other != null && startsAt.isBefore(other.endsAt()) && other.startsAt().isBefore(endsAt);
    }

    public boolean sharesRoomWith(SurgerySchedule other) {
        return other != null && roomId.equals(other.roomId);
    }

    public boolean sharesStaffWith(SurgerySchedule other) {
        if (other == null) {
            return false;
        }
        Set<UUID> staff = teamAssignments.stream().map(SurgeryTeamAssignment::staffId)
                .collect(java.util.stream.Collectors.toSet());
        return other.teamAssignments.stream().anyMatch(assignment -> staff.contains(assignment.staffId()));
    }

    public boolean conflictsWith(SurgerySchedule other) {
        return overlaps(other) && (sharesRoomWith(other) || sharesStaffWith(other));
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code, "Lịch dự kiến hoặc phân công ê-kíp không hợp lệ");
    }
}
