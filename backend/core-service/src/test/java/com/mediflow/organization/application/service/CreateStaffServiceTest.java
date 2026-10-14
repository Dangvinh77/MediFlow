package com.mediflow.organization.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.DepartmentInactiveException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

@ExtendWith(MockitoExtension.class)
class CreateStaffServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Test
    void execute_inactiveDepartment_throwsBusinessRule() {
        UUID departmentId = UUID.randomUUID();
        Department department = Department.create(
                departmentId, "Clinical", "CLN", DepartmentType.CLINICAL, "Building A");
        department.deactivate(false);
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));

        CreateStaffService service = new CreateStaffService(
                staffRepository, departmentRepository, eventPublisher);

        assertThrows(DepartmentInactiveException.class, () -> service.execute(
                "Alex Morgan", departmentId, JobTitle.NURSE, null, null,
                "0901234567", "alex@example.com"));
    }

    @Test
    void execute_activeDepartment_savesAndPublishesStaffEvent() {
        UUID departmentId = UUID.randomUUID();
        Department department = Department.create(
                departmentId, "Clinical", "CLN", DepartmentType.CLINICAL, "Building A");
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(staffRepository.save(any(Staff.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateStaffService service = new CreateStaffService(
                staffRepository, departmentRepository, eventPublisher);

        service.execute("Alex Morgan", departmentId, JobTitle.NURSE, null, null,
                "0901234567", "alex@example.com");

        org.mockito.Mockito.verify(eventPublisher)
                .publishStaffCreated(any(com.mediflow.organization.application.event.StaffCreatedEvent.class));
    }
}
