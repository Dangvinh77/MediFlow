package com.mediflow.organization.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

@ExtendWith(MockitoExtension.class)
class ChangeStaffDepartmentServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Test
    void execute_sameDepartment_isNoOpWithoutSaveOrEvent() {
        UUID departmentId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Staff staff = Staff.create("Alex Morgan", departmentId, JobTitle.NURSE,
                null, null, "0901234567", "alex@example.com");
        Department department = Department.create(
                departmentId, "Clinical", "CLN", DepartmentType.CLINICAL, "Building A");
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));

        ChangeStaffDepartmentService service = new ChangeStaffDepartmentService(
                staffRepository, departmentRepository, eventPublisher);

        Staff result = service.execute(staffId, departmentId);

        assertEquals(staff, result);
        verify(staffRepository, never()).save(any());
        verify(eventPublisher, never()).publishStaffDepartmentChanged(any());
    }

    @Test
    void execute_validTransfer_savesAndPublishesChangedEvent() {
        UUID oldDepartmentId = UUID.randomUUID();
        UUID newDepartmentId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Staff staff = Staff.create("Alex Morgan", oldDepartmentId, JobTitle.NURSE,
                null, null, "0901234567", "alex@example.com");
        Department department = Department.create(
                newDepartmentId, "Surgery", "SUR", DepartmentType.CLINICAL, "Building B");
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(departmentRepository.findById(newDepartmentId)).thenReturn(Optional.of(department));
        when(staffRepository.save(any(Staff.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChangeStaffDepartmentService service = new ChangeStaffDepartmentService(
                staffRepository, departmentRepository, eventPublisher);

        service.execute(staffId, newDepartmentId);

        verify(eventPublisher).publishStaffDepartmentChanged(any());
    }
}
