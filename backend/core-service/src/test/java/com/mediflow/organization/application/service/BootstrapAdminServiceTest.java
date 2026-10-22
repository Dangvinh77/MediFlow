package com.mediflow.organization.application.service;

import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.domain.model.*;
import org.junit.jupiter.api.Test;

class BootstrapAdminServiceTest {
    @Test void skipsAllCreationWhenAnyAdminAlreadyExists() {
        var accounts = mock(AccountRepository.class);
        var departments = mock(CreateDepartmentUseCase.class);
        var staff = mock(CreateStaffUseCase.class);
        var create = mock(CreateAccountUseCase.class);
        var admin = Account.create(UUID.randomUUID(), "existing-admin", "hash", UUID.randomUUID(), Role.ADMIN);
        when(accounts.search(null, Role.ADMIN, null, PageQuery.of(0, 1))).thenReturn(PageResult.of(List.of(admin), 1, 0, 1));
        new BootstrapAdminService(accounts, departments, staff, create).execute("new-admin", "password", "012345678901", "Name", "QTHT");
        verifyNoInteractions(departments, staff, create);
    }
    @Test void firstAdminUsesTheCreatedStaffIdentityAndRequiredIdentityNumber() {
        var accounts = mock(AccountRepository.class);
        var departments = mock(CreateDepartmentUseCase.class);
        var staff = mock(CreateStaffUseCase.class);
        var create = mock(CreateAccountUseCase.class);
        var department = Department.create(UUID.randomUUID(), "Phòng Quản trị hệ thống", "QTHT", DepartmentType.ADMINISTRATIVE, null);
        var employee = Staff.create("Admin Name", department.getDepartmentId(), JobTitle.ADMINISTRATIVE, null, null, null, null, "012345678901");
        when(accounts.search(null, Role.ADMIN, null, PageQuery.of(0, 1))).thenReturn(PageResult.empty(PageQuery.of(0, 1)));
        when(departments.execute("Phòng Quản trị hệ thống", "QTHT", DepartmentType.ADMINISTRATIVE, null)).thenReturn(department);
        when(staff.execute("Admin Name", department.getDepartmentId(), JobTitle.ADMINISTRATIVE, null, null, null, null, "012345678901")).thenReturn(employee);
        new BootstrapAdminService(accounts, departments, staff, create).execute("local-admin", "strong-password", "012345678901", "Admin Name", "QTHT");
        verify(create).execute("local-admin", "strong-password", employee.getStaffId(), null, Role.ADMIN);
    }
}
