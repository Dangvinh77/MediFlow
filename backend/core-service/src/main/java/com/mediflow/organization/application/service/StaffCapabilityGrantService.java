package com.mediflow.organization.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.organization.application.dto.request.CapabilityGrantCommand;
import com.mediflow.organization.application.dto.request.CapabilityRevocationCommand;
import com.mediflow.organization.application.dto.response.LabReleaseAuthority;
import com.mediflow.organization.application.dto.response.RecordReviewAuthority;
import com.mediflow.organization.application.dto.response.StaffCapabilityGrantDTO;
import com.mediflow.organization.application.exception.SchedulingScopeDeniedException;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository;
import com.mediflow.organization.application.port.out.StaffCapabilityGrantRepository;
import com.mediflow.organization.domain.model.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Administrators decide which laboratory staff may release results; the capability is never implied by a role. */
@Service
public class StaffCapabilityGrantService {
    private static final String LAB_RESULT_RELEASE = "LAB_RESULT_RELEASE";
    private static final String MEDICAL_RECORD_REVIEW = "MEDICAL_RECORD_REVIEW";

    private final StaffCapabilityGrantRepository grants;
    private final SchedulingAuthorityRepository authority;

    public StaffCapabilityGrantService(
            StaffCapabilityGrantRepository grants, SchedulingAuthorityRepository authority) {
        this.grants = grants;
        this.authority = authority;
    }

    @Transactional(transactionManager = "organizationTransactionManager")
    public StaffCapabilityGrantDTO grant(
            UUID actorId, Role role, UUID staffId, CapabilityGrantCommand command) {
        if (command == null || !(LAB_RESULT_RELEASE.equals(command.capability())
                || MEDICAL_RECORD_REVIEW.equals(command.capability()))) {
            throw new IllegalArgumentException("Unsupported capability");
        }
        requireReason(command.reason());
        requireAdmin(actorId, role);
        var target = grants.lockTarget(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Staff not found"));
        Role requiredRole = MEDICAL_RECORD_REVIEW.equals(command.capability()) ? Role.DOCTOR : Role.LAB_TECH;
        if (target.role() != requiredRole || !target.staffActive() || !target.accountActive()) {
            throw new BusinessRuleException(
                    "CAPABILITY_GRANT_INELIGIBLE",
                    "An active " + requiredRole + " account and staff record are required");
        }
        var grant = new StaffCapabilityGrantDTO(UUID.randomUUID(), staffId, command.capability(), actorId,
                command.reason().trim(), now(), null, null, null);
        if (!grants.insertIfNoActive(grant)) {
            throw new DuplicateResourceException(
                    "CAPABILITY_GRANT_ACTIVE", "This staff member already holds the capability");
        }
        return grant;
    }

    @Transactional(transactionManager = "organizationTransactionManager")
    public StaffCapabilityGrantDTO revoke(
            UUID actorId, Role role, UUID grantId, CapabilityRevocationCommand command) {
        requireReason(command == null ? null : command.reason());
        requireAdmin(actorId, role);
        var grant = grants.lockGrant(grantId)
                .orElseThrow(() -> new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Capability grant not found"));
        if (grant.revokedAt() != null) {
            throw new BusinessRuleException("CAPABILITY_GRANT_ALREADY_REVOKED", "Capability grant is already revoked");
        }
        Instant at = now();
        String reason = command.reason().trim();
        grants.revoke(grantId, actorId, reason, at);
        return new StaffCapabilityGrantDTO(grant.grantId(), grant.staffId(), grant.capability(), grant.grantedBy(),
                grant.grantReason(), grant.grantedAt(), actorId, reason, at);
    }

    /** Read-only history for the ADMIN capability screen; the endpoint itself is ADMIN-only. */
    @Transactional(transactionManager = "organizationTransactionManager", readOnly = true)
    public List<StaffCapabilityGrantDTO> list(UUID staffId) {
        return grants.findByStaff(staffId);
    }

    @Transactional(transactionManager = "organizationTransactionManager", readOnly = true)
    public LabReleaseAuthority labReleaseAuthority(UUID actorAccountId) {
        var data = grants.findLabRelease(actorAccountId);
        boolean canRelease = data.accountExists() && data.accountActive() && data.role() == Role.LAB_TECH
                && data.staffActive() && data.hasActiveGrant();
        return new LabReleaseAuthority(actorAccountId, data.accountActive(), data.staffId(), data.staffActive(),
                canRelease, Instant.now());
    }

    @Transactional(transactionManager = "organizationTransactionManager", readOnly = true)
    public RecordReviewAuthority recordReviewAuthority(UUID actorAccountId) {
        var data = grants.findRecordReview(actorAccountId);
        boolean canReview = data.accountExists() && data.accountActive() && data.role() == Role.DOCTOR
                && data.staffActive() && data.hasActiveGrant();
        return new RecordReviewAuthority(actorAccountId, data.staffId(), canReview);
    }

    private void requireAdmin(UUID actorId, Role role) {
        var admin = authority.lockAccount(actorId).orElseThrow(SchedulingScopeDeniedException::new);
        if (!admin.active() || admin.role() != Role.ADMIN || role != Role.ADMIN) {
            throw new SchedulingScopeDeniedException();
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("A reason of at most 500 characters is required");
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
