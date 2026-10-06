package com.mediflow.surgery.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Organization hint: it can invalidate readiness, never grant clinical permission. */
public record SurgeryAuthorityChange(ReferenceKind referenceKind, UUID referenceId, SurgeryTeamRole teamRole,
        long revision, Instant occurredAt, UUID actorAccountId, String reason, String fingerprint) {
    public enum ReferenceKind { ROOM, STAFF_CAPABILITY }

    public SurgeryAuthorityChange {
        if (referenceKind == null || referenceId == null || revision < 1 || occurredAt == null
                || actorAccountId == null || reason == null || reason.isBlank() || reason.length() > 500
                || !reason.equals(reason.trim()) || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")
                || (referenceKind == ReferenceKind.ROOM && teamRole != null)
                || (referenceKind == ReferenceKind.STAFF_CAPABILITY && teamRole == null)) {
            throw new IllegalArgumentException("Invalid Organization authority hint");
        }
    }

    public String referenceKey() {
        return referenceKind + ":" + referenceId + (teamRole == null ? "" : ":" + teamRole);
    }

    public boolean affects(SurgerySchedule schedule) {
        return referenceKind == ReferenceKind.ROOM ? referenceId.equals(schedule.roomId())
                : schedule.teamAssignments().stream().anyMatch(member ->
                referenceId.equals(member.staffId()) && teamRole == member.role());
    }
}
