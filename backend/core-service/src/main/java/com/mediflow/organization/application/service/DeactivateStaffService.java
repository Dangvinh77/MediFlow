package com.mediflow.organization.application.service;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.event.StaffDeactivatedEvent;
import com.mediflow.organization.application.port.in.DeactivateStaffUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.DepartmentHeadRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.Staff;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

@Transactional("organizationTransactionManager")
public class DeactivateStaffService implements DeactivateStaffUseCase {
    private final StaffRepository staff;
    private final DepartmentRepository departments;
    private final AccountRepository accounts;
    private final EventPublisher events;
    private final CorrelationIdProvider correlationIds;
    private final DepartmentHeadRepository heads;

    public DeactivateStaffService(StaffRepository staff, DepartmentRepository departments,
            AccountRepository accounts, EventPublisher events, CorrelationIdProvider correlationIds, DepartmentHeadRepository heads) {
        this.staff = staff;
        this.departments = departments;
        this.accounts = accounts;
        this.events = events;
        this.correlationIds = correlationIds;
        this.heads = heads;
    }

    @Override
    public Staff execute(UUID staffId, UUID actorAccountId) {
        Objects.requireNonNull(actorAccountId, "Authenticated actor is required");
        // Head appointments take staff/account locks in a different order; serialize with their existing command lock first.
        heads.lockCommands();
        // Owner lock serializes issuance/status; account rows precede staff rows, as in email changes.
        accounts.lockOwner(staffId, null);
        var linked = accounts.findActiveByOwner(staffId, null);
        if (linked.stream().anyMatch(account -> account.getAccountId().equals(actorAccountId))) {
            throw new DuplicateResourceException("STAFF_SELF_DEACTIVATION",
                    "Không thể ngừng làm việc hồ sơ của chính mình. Hãy nhờ quản trị viên khác thực hiện.");
        }
        Staff member = staff.findByIdForUpdate(staffId).orElseThrow(() -> new StaffNotFoundException(staffId));
        if (!member.isActive()) return member;
        var department = departments.findByIdForUpdate(member.getDepartmentId())
                .orElseThrow(() -> new DepartmentNotFoundException(member.getDepartmentId()));
        if (staffId.equals(department.getDepartmentHeadId())) {
            throw new DuplicateResourceException("STAFF_DEPARTMENT_HEAD",
                    "Hãy thay hoặc gỡ trưởng khoa/phòng trước khi ngừng làm việc nhân viên này.");
        }
        member.deactivate();
        Staff saved = staff.save(member);
        for (var account : linked) {
            account.deactivate();
            accounts.save(account);
            accounts.incrementCredentialVersion(account.getAccountId());
        }
        events.publishStaffDeactivated(new StaffDeactivatedEvent(UUID.randomUUID(), saved.getUpdatedAt(),
                correlationIds.currentOrCreate().toString(), staffId, actorAccountId));
        return saved;
    }
}
