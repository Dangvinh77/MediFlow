package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetOwnStaffDepartmentServiceTest {
    private final StaffRepository staff = mock(StaffRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final GetOwnStaffDepartmentService service = new GetOwnStaffDepartmentService(staff, departments);

    @Test
    void deniesForeignAndMissingIdentityBeforeAnyStorageAccess() {
        UUID id = UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(() -> service.read(id, UUID.randomUUID()));
        assertThatIllegalArgumentException().isThrownBy(() -> service.read(id, null));
        verifyNoInteractions(staff, departments);
    }

    @Test
    void returnsOnlyActualDepartmentIdentityAndName() {
        var unit = Department.create(UUID.randomUUID(), "Khoa Nội", "NOI", DepartmentType.CLINICAL, null);
        var member = Staff.create("Member", unit.getDepartmentId(), JobTitle.NURSE, null, null, null, null, "012345678901");
        when(staff.findById(member.getStaffId())).thenReturn(Optional.of(member));
        when(departments.findById(unit.getDepartmentId())).thenReturn(Optional.of(unit));
        var result = service.read(member.getStaffId(), member.getStaffId());
        assertThat(result.departmentId()).isEqualTo(unit.getDepartmentId());
        assertThat(result.departmentName()).isEqualTo("Khoa Nội");
    }
}
