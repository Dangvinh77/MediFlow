package com.mediflow.organization.application.port.out;

import com.mediflow.organization.application.dto.response.StaffCapabilityGrantDTO;
import com.mediflow.organization.domain.model.Role;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffCapabilityGrantRepository {
    /** Locks the staff member and their account; role is null unless exactly one account exists. */
    Optional<GrantTarget> lockTarget(UUID staffId);

    /** @return false when an active grant for the same staff member and capability already exists */
    boolean insertIfNoActive(StaffCapabilityGrantDTO grant);

    Optional<StaffCapabilityGrantDTO> lockGrant(UUID grantId);

    void revoke(UUID grantId, UUID actorId, String reason, Instant at);

    /** Every grant of one staff member, active and revoked, newest first. */
    List<StaffCapabilityGrantDTO> findByStaff(UUID staffId);

    LabReleaseData findLabRelease(UUID accountId);

    RecordReviewData findRecordReview(UUID accountId);

    record GrantTarget(UUID staffId, Role role, boolean staffActive, boolean accountActive) {}

    record LabReleaseData(boolean accountExists, boolean accountActive, Role role, UUID staffId, boolean staffActive,
            boolean hasActiveGrant) {}

    record RecordReviewData(boolean accountExists, boolean accountActive, Role role, UUID staffId, boolean staffActive,
            boolean hasActiveGrant) {}
}
