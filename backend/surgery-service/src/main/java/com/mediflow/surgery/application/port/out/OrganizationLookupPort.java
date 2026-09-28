package com.mediflow.surgery.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Local port for authoritative Organization lookups. This is not a wire DTO;
 * its HTTP shape and eligibility mapping remain gated by the Organization handoff.
 */
public interface OrganizationLookupPort {

    OrganizationLookupSnapshot findRoom(UUID roomId);

    OrganizationLookupSnapshot findStaff(UUID staffId);

    OrganizationLookupSnapshot findDepartment(UUID departmentId);

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
            String jobTitleCode) {

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
            sourceRevision = sourceRevision == null ? null : sourceRevision.trim();
            jobTitleCode = jobTitleCode == null ? null : jobTitleCode.trim();
        }
    }
}
