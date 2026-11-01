package com.mediflow.organization.application.service;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.organization.application.dto.request.DepartmentHeadCommand;
import com.mediflow.organization.application.dto.response.DepartmentHeadSnapshot;
import com.mediflow.organization.application.dto.response.OwnCapabilities;
import com.mediflow.organization.application.exception.SchedulingScopeDeniedException;
import com.mediflow.organization.application.port.out.DepartmentHeadRepository;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository;
import com.mediflow.organization.domain.model.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class DepartmentHeadService {
    private final DepartmentHeadRepository heads;
    private final SchedulingAuthorityRepository authority;

    public DepartmentHeadService(
            DepartmentHeadRepository heads, SchedulingAuthorityRepository authority) {
        this.heads = heads;
        this.authority = authority;
    }

    private void actor(UUID id, Role role, boolean admin) {
        var a = authority.findAccount(id).orElseThrow(SchedulingScopeDeniedException::new);
        if (!a.active()
                || a.role() != role
                || role == Role.SYSTEM
                || role == Role.PATIENT
                || (admin && role != Role.ADMIN)) throw new SchedulingScopeDeniedException();
    }

    @Transactional(
            transactionManager = "organizationTransactionManager",
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public DepartmentHeadSnapshot get(UUID actorId, Role role, UUID departmentId) {
        actor(actorId, role, true);
        authority
                .findDepartment(departmentId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "RESOURCE_NOT_FOUND", "Department not found"));
        return heads.snapshot(departmentId);
    }

    @Transactional(
            transactionManager = "organizationTransactionManager",
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public OwnCapabilities capabilities(UUID actorId, Role role) {
        actor(actorId, role, false);
        return heads.capabilities(actorId, Instant.now());
    }

    @Transactional(transactionManager = "organizationTransactionManager")
    public Result replace(
            UUID actorId,
            Role role,
            UUID departmentId,
            DepartmentHeadCommand command,
            String key,
            String correlationId) {
        if (command == null
                || command.expectedRevision() == null
                || command.expectedRevision() < 0
                || command.reason() == null
                || command.reason().isBlank()
                || command.reason().length() > 500
                || key == null
                || !key.matches("[!-~]{1,120}")
                || correlationId == null
                || correlationId.isBlank()
                || !correlationId.matches("[ -~]{1,120}"))
            throw new IllegalArgumentException("Invalid appointment command");
        heads.lockCommands();
        var admin = authority.lockAccount(actorId).orElseThrow(SchedulingScopeDeniedException::new);
        if (!admin.active() || admin.role() != Role.ADMIN || role != Role.ADMIN)
            throw new SchedulingScopeDeniedException();
        String fingerprint =
                command.expectedRevision() + "|" + command.staffId() + "|" + command.reason();
        var receipt = heads.receipt(actorId, departmentId, key);
        if (receipt.isPresent()) {
            if (!receipt.get().command().equals(fingerprint))
                throw new DuplicateResourceException(
                        "IDEMPOTENCY_CONFLICT", "Idempotency key was already used");
            return new Result(receipt.get().response(), true);
        }
        // Staff before department: matches membership-change locking order.
        UUID targetAccount = null;
        if (command.staffId() != null)
            targetAccount =
                    heads.lockEligibleDoctorAccount(command.staffId(), departmentId)
                            .orElseThrow(
                                    () ->
                                            new BusinessRuleException(
                                                    "HEAD_INELIGIBLE",
                                                    "An active DOCTOR account and staff in the same"
                                                        + " clinical department are required"));
        boolean clinical = heads.lockClinicalDepartment(departmentId);
        var before = heads.snapshot(departmentId);
        if (before.revision() != command.expectedRevision())
            throw new DuplicateResourceException(
                    "HEAD_REVISION_CONFLICT", "Department head revision changed");
        if (command.staffId() != null
                && (!clinical || heads.hasActiveStaffAppointment(command.staffId())))
            throw new BusinessRuleException(
                    "HEAD_INELIGIBLE",
                    "An active CLINICAL department and an unappointed doctor are required");
        heads.replace(
                actorId,
                departmentId,
                command.staffId(),
                targetAccount,
                command.staffId() == null ? null : UUID.randomUUID(),
                before.revision() + 1,
                command.reason(),
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                correlationId);
        var result = heads.snapshot(departmentId);
        heads.receipt(actorId, departmentId, key, fingerprint, result);
        return new Result(result, false);
    }

    public record Result(DepartmentHeadSnapshot snapshot, boolean replayed) {}
}
