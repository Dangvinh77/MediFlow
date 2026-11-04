package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.in.UpdateStaffUseCase;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.InvalidDepartmentHeadException;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import org.springframework.transaction.annotation.Transactional;
import com.mediflow.organization.domain.model.StaffProfile;

import java.util.UUID;
import java.time.Instant;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.StaffIdentityAuditPort;
import com.mediflow.organization.domain.exception.InvalidStaffDataException;

@Transactional("organizationTransactionManager")
public class UpdateStaffService implements UpdateStaffUseCase {

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final AccountRepository accounts;
    private final StaffIdentityAuditPort identityAudit;

    public UpdateStaffService(StaffRepository staffRepository, DepartmentRepository departmentRepository,
            AccountRepository accounts, StaffIdentityAuditPort identityAudit) {
        this.staffRepository = staffRepository;
        this.departmentRepository = departmentRepository;
        this.accounts = accounts;
        this.identityAudit = identityAudit;
    }

    @Override
    public Staff execute(
            UUID staffId,
            String fullName,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email
    ) {
        return execute(staffId, fullName, jobTitle, specialization, licenseNumber, phoneNumber, email, null);
    }

    @Override
    public Staff execute(UUID staffId, String fullName, JobTitle jobTitle, String specialization, String licenseNumber, String phoneNumber, String email, StaffProfile profile) {
        return execute(staffId, fullName, jobTitle, specialization, licenseNumber, phoneNumber, email,
                profile, null, null);
    }

    @Override
    public Staff execute(UUID staffId, String fullName, JobTitle jobTitle, String specialization, String licenseNumber,
            String phoneNumber, String email, StaffProfile profile, String identityNumber, String identityChangeReason) {
        return execute(staffId, fullName, jobTitle, specialization, licenseNumber, phoneNumber, email,
                profile, identityNumber, identityChangeReason, null);
    }

    @Override
    public Staff execute(UUID staffId, String fullName, JobTitle jobTitle, String specialization, String licenseNumber,
            String phoneNumber, String email, StaffProfile profile, String identityNumber,
            String identityChangeReason, UUID changedByAccountId) {
        Staff staff = staffRepository.findByIdForUpdate(staffId)
                .orElseThrow(() -> new StaffNotFoundException(staffId));

        var department = departmentRepository.findByIdForUpdate(staff.getDepartmentId())
                .orElseThrow(() -> new DepartmentNotFoundException(staff.getDepartmentId()));
        if (staffId.equals(department.getDepartmentHeadId()) && department.getDepartmentType() != com.mediflow.organization.domain.model.DepartmentType.ADMINISTRATIVE && jobTitle != JobTitle.DOCTOR) {
            throw new InvalidDepartmentHeadException(
                    "Remove or reassign the department head before changing their professional title");
        }

        String normalizedEmail = email == null ? staff.getEmail() : email.trim();
        if (!java.util.Objects.equals(normalizedEmail, staff.getEmail())
                && accounts != null && accounts.existsByStaffId(staffId)) {
            throw new InvalidStaffDataException("Staff with a linked account must change email from account management");
        }
        staff.update(
                fullName,
                jobTitle,
                specialization,
                licenseNumber,
                phoneNumber,
                normalizedEmail
        );

        if (profile != null) staff.withProfile(profile);
        boolean identityChanged = identityNumber != null && !identityNumber.equals(staff.getIdentityNumber());
        String oldIdentity = staff.getIdentityNumber();
        if (!identityChanged && identityChangeReason != null && !identityChangeReason.isBlank()) {
            throw new InvalidStaffDataException("Identity change reason is only accepted when the identity number changes");
        }
        if (identityChanged) {
            if (identityChangeReason == null || identityChangeReason.isBlank() || identityChangeReason.trim().length() > 500) {
                throw new InvalidStaffDataException("A reason is required to correct staff identity number");
            }
            staff.correctIdentityNumber(identityNumber.trim());
        }
        Staff saved = staffRepository.save(staff);
        if (identityChanged) {
            if (identityAudit == null || changedByAccountId == null) {
                throw new InvalidStaffDataException("Authenticated admin audit is required for identity correction");
            }
            identityAudit.record(staffId, changedByAccountId, identityChangeReason.trim(), oldIdentity, saved.getIdentityNumber(),
                    saved.getUpdatedAt() == null ? Instant.now() : saved.getUpdatedAt());
        }
        return saved;
    }
}
