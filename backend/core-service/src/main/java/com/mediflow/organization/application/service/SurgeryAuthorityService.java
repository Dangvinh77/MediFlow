package com.mediflow.organization.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.organization.application.dto.request.*;
import com.mediflow.organization.application.dto.response.*;
import com.mediflow.organization.application.event.SurgeryAuthorityChangedEvent;
import com.mediflow.organization.application.mapper.SurgeryAuthorityMapper;
import com.mediflow.organization.application.port.in.ManageSurgeryAuthorityUseCase;
import com.mediflow.organization.application.port.out.*;
import com.mediflow.organization.domain.model.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Authority decisions do not reserve surgical resources or attest clinical/legal compliance. */
public class SurgeryAuthorityService implements ManageSurgeryAuthorityUseCase {
    private final SurgeryAuthorityRepository authority;
    private final StaffRepository staff;
    private final DepartmentRepository departments;
    private final SurgeryAuthorityEventPort events;
    private final CorrelationIdProvider correlations;
    private final Clock clock;
    private final SurgeryAuthorityMapper dtoMapper;

    public SurgeryAuthorityService(SurgeryAuthorityRepository authority, StaffRepository staff,
            DepartmentRepository departments, SurgeryAuthorityEventPort events,
            CorrelationIdProvider correlations, Clock clock, SurgeryAuthorityMapper dtoMapper) {
        this.authority = authority;
        this.staff = staff;
        this.departments = departments;
        this.events = events;
        this.correlations = correlations;
        this.clock = clock;
        this.dtoMapper = dtoMapper;
    }

    @Override
    @Transactional
    public OperatingRoomDTO saveRoom(UUID roomId, OperatingRoomRequest request, UUID actorAccountId) {
        requireDecision(actorAccountId, request.reason(), request.expectedRevision());
        Department department = department(request.departmentId());
        // An existing room can be deactivated even after its department changed type/status.
        if (request.active() || request.expectedRevision() == 0) {
            requireClinicalDepartment(department, request.active());
        }
        Instant now = clock.instant();
        OperatingRoom room = new OperatingRoom(roomId, request.roomCode(), request.roomName(),
                request.departmentId(), request.active(), request.expectedRevision() + 1, now);
        authority.saveRoom(room, request.expectedRevision());
        append("ROOM", roomId, null, room.revision(), actorAccountId, request.reason(), now);
        return dtoMapper.toDto(room);
    }

    @Override
    @Transactional
    public SurgicalCapabilityDTO decideCapability(UUID staffId, SurgicalTeamRole role,
            SurgicalCapabilityRequest request, UUID actorAccountId) {
        requireDecision(actorAccountId, request.reason(), request.expectedRevision());
        Staff member = staff.findById(staffId).orElseThrow(() ->
                new ResourceNotFoundException("STAFF_NOT_FOUND", "Staff reference not found"));
        Department department = department(member.getDepartmentId());
        // Revocation must remain possible after a staff/department has become inactive.
        if (request.active()) {
            requireClinicalDepartment(department, true);
            if (role == null || !role.accepts(member)) {
                throw new BusinessRuleException("ORG_STAFF_INELIGIBLE",
                        "Staff job title/license/status cannot support this capability");
            }
        }
        Instant now = clock.instant();
        SurgicalCapability capability = new SurgicalCapability(staffId, role,
                member.getDepartmentId(), request.active(), request.validFrom(), request.validUntil(),
                request.expectedRevision() + 1, now);
        authority.saveCapability(capability, request.expectedRevision());
        append("STAFF_CAPABILITY", staffId, role.name(), capability.revision(),
                actorAccountId, request.reason(), now);
        return dtoMapper.toDto(capability);
    }

    @Override
    @Transactional(readOnly = true)
    public OperatingRoomLookupDTO lookupRoom(UUID roomId) {
        OperatingRoom room = authority.findRoom(roomId).orElse(null);
        if (room == null) return new OperatingRoomLookupDTO(false, false, roomId,
                null, null, clock.instant());
        Department department = departments.findById(room.departmentId()).orElse(null);
        boolean active = room.active() && department != null && department.isActive()
                && department.getDepartmentType() == DepartmentType.CLINICAL;
        return new OperatingRoomLookupDTO(true, active, roomId, room.departmentId(),
                Long.toString(room.revision()), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public SurgicalEligibilityDTO lookupEligibility(UUID staffId, SurgicalTeamRole role,
            Instant startsAt, Instant endsAt) {
        if (staffId == null || role == null || startsAt == null || endsAt == null
                || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Surgical lookup needs a valid role and half-open interval");
        }
        Staff member = staff.findById(staffId).orElse(null);
        if (member == null) return new SurgicalEligibilityDTO(false, false, staffId, role.name(),
                null, null, clock.instant(), startsAt, endsAt);
        Department department = departments.findById(member.getDepartmentId()).orElse(null);
        SurgicalCapability capability = authority.findCapability(staffId, role).orElse(null);
        boolean eligible = capability != null && capability.covers(member, department, startsAt, endsAt);
        return new SurgicalEligibilityDTO(true, eligible, staffId, role.name(), member.getDepartmentId(),
                capability == null ? null : Long.toString(capability.revision()),
                clock.instant(), startsAt, endsAt);
    }

    private Department department(UUID id) {
        return departments.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("DEPARTMENT_NOT_FOUND", "Department reference not found"));
    }

    private static void requireClinicalDepartment(Department department, boolean activating) {
        if (department.getDepartmentType() != DepartmentType.CLINICAL
                || activating && !department.isActive()) {
            throw new BusinessRuleException("ORG_SURGERY_DEPARTMENT_INELIGIBLE",
                    "Surgical authority requires an active clinical department");
        }
    }

    private static void requireDecision(UUID actor, String reason, Long revision) {
        if (actor == null || revision == null || revision < 0 || revision == Long.MAX_VALUE
                || reason == null || reason.isBlank() || reason.length() > 500) {
            throw new BusinessRuleException("ORG_AUTHORITY_DECISION_INVALID",
                    "Verified actor, reason and valid expected revision are required");
        }
    }

    private void append(String kind, UUID id, String role, long revision,
            UUID actor, String reason, Instant now) {
        events.append(new SurgeryAuthorityChangedEvent(UUID.randomUUID(),
                SurgeryAuthorityChangedEvent.ROUTING_KEY, 1, now, correlations.currentOrCreate(),
                "organization-service", new SurgeryAuthorityChangedEvent.Payload(
                        kind, id, role, revision, actor, reason.trim())));
    }
}
