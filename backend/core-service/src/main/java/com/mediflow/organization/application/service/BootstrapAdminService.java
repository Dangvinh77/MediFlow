package com.mediflow.organization.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.organization.application.port.in.*;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.domain.model.*;
import org.springframework.transaction.annotation.Transactional;

@Transactional("organizationTransactionManager")
public class BootstrapAdminService implements BootstrapAdminUseCase {
    private final AccountRepository accounts;
    private final CreateDepartmentUseCase departments;
    private final CreateStaffUseCase staff;
    private final CreateAccountUseCase createAccount;

    public BootstrapAdminService(AccountRepository accounts, CreateDepartmentUseCase departments,
            CreateStaffUseCase staff, CreateAccountUseCase createAccount) {
        this.accounts = accounts; this.departments = departments;
        this.staff = staff; this.createAccount = createAccount;
    }

    @Override
    public void execute(String username, String password, String identityNumber, String fullName, String abbreviation) {
        if (accounts.search(null, Role.ADMIN, null, PageQuery.of(0, 1)).totalElements() > 0) return;
        if (username == null || !username.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new com.mediflow.organization.domain.exception.InvalidAccountException(
                    "Bootstrap administrator must use an email address for login");
        }
        String email = username.trim().toLowerCase(java.util.Locale.ROOT);
        var department = departments.execute("Phòng Quản trị hệ thống", abbreviation, DepartmentType.ADMINISTRATIVE, null);
        var employee = staff.execute(fullName, department.getDepartmentId(), JobTitle.ADMINISTRATIVE,
                null, null, null, email, identityNumber);
        createAccount.execute(email, password, employee.getStaffId(), null, Role.ADMIN);
    }
}
