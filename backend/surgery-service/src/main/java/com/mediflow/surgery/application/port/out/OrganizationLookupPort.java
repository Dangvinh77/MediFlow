package com.mediflow.surgery.application.port.out;

import java.time.Instant;
import java.util.UUID;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;

/**
 * Local port for authoritative Organization lookups. Staff and department have
 * phase-1 identity and additive V1 room/capability contracts. Snapshots are not leases.
 */
public interface OrganizationLookupPort {

    OrganizationLookupSnapshot findRoom(UUID roomId, String correlationId);

    OrganizationLookupSnapshot findStaff(UUID staffId, String correlationId);

    OrganizationLookupSnapshot findDepartment(UUID departmentId, String correlationId);

    SurgicalEligibilitySnapshot findSurgicalEligibility(UUID staffId, SurgeryTeamRole role,
            Instant startsAt, Instant endsAt, String correlationId);

    record SurgicalEligibilitySnapshot(UUID staffId, SurgeryTeamRole teamRole, ReferenceState state,
            UUID departmentId, Instant observedAt, String sourceRevision, Instant startsAt, Instant endsAt) {
        public SurgicalEligibilitySnapshot {
            if (staffId == null || teamRole == null || state == null || observedAt == null
                    || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
                throw new IllegalArgumentException("Surgical eligibility identity/interval is required");
            }
        }
    }

    enum ReferenceKind {
        ROOM,
        STAFF,
        DEPARTMENT
    }

    enum ReferenceState {
        ACTIVE,
        INACTIVE,
        NOT_FOUND,
        UNKNOWN
    }

    record OrganizationLookupSnapshot(
            ReferenceKind kind,
            UUID referenceId,
            ReferenceState state,
            Instant observedAt,
            String sourceRevision,
            String jobTitleCode,
            UUID staffDepartmentId,
            UUID roomDepartmentId) {

        public OrganizationLookupSnapshot(ReferenceKind kind, UUID referenceId,
                                          ReferenceState state, Instant observedAt,
                                          String sourceRevision, String jobTitleCode,
                                          UUID staffDepartmentId) {
            this(kind, referenceId, state, observedAt, sourceRevision, jobTitleCode, staffDepartmentId, null);
        }

        public OrganizationLookupSnapshot(ReferenceKind kind, UUID referenceId,
                                          ReferenceState state, Instant observedAt,
                                          String sourceRevision, String jobTitleCode) {
            this(kind, referenceId, state, observedAt, sourceRevision, jobTitleCode, null);
        }

        public OrganizationLookupSnapshot {
            if (kind == null || referenceId == null || state == null || observedAt == null) {
                throw new IllegalArgumentException("Organization lookup identity and observation are required");
            }
            if (sourceRevision != null && sourceRevision.isBlank()) {
                throw new IllegalArgumentException("Organization source revision must not be blank");
            }
            if (jobTitleCode != null && jobTitleCode.isBlank()) {
                throw new IllegalArgumentException("Organization job title must not be blank");
            }
            if (jobTitleCode != null && kind != ReferenceKind.STAFF) {
                throw new IllegalArgumentException("Job title applies only to a staff lookup");
            }
            if (staffDepartmentId != null && kind != ReferenceKind.STAFF) {
                throw new IllegalArgumentException("Staff department applies only to a staff lookup");
            }
            if (roomDepartmentId != null && kind != ReferenceKind.ROOM) {
                throw new IllegalArgumentException("Room department applies only to a room lookup");
            }
            sourceRevision = sourceRevision == null ? null : sourceRevision.trim();
            jobTitleCode = jobTitleCode == null ? null : jobTitleCode.trim();
        }
    }
}
