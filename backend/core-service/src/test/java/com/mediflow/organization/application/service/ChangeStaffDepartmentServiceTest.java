package com.mediflow.organization.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.mediflow.organization.domain.exception.InvalidDepartmentHeadException;
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
                null, null, "0901234567", "alex@example.com", "012345678901");
        Department department = Department.create(
                departmentId, "Clinical", "CLN", DepartmentType.CLINICAL, "Building A");
        when(staffRepository.findByIdForUpdate(staffId)).thenReturn(Optional.of(staff));
        when(departmentRepository.findByIdForUpdate(departmentId)).thenReturn(Optional.of(department));

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
                null, null, "0901234567", "alex@example.com", "012345678901");
        Department department = Department.create(
                newDepartmentId, "Surgery", "SUR", DepartmentType.CLINICAL, "Building B");
        Department oldDepartment = Department.create(
                oldDepartmentId, "Clinical", "CLN", DepartmentType.CLINICAL, "Building A");
        when(staffRepository.findByIdForUpdate(staffId)).thenReturn(Optional.of(staff));
        when(departmentRepository.findByIdForUpdate(newDepartmentId)).thenReturn(Optional.of(department));
        when(departmentRepository.findByIdForUpdate(oldDepartmentId)).thenReturn(Optional.of(oldDepartment));
        when(staffRepository.save(any(Staff.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChangeStaffDepartmentService service = new ChangeStaffDepartmentService(
                staffRepository, departmentRepository, eventPublisher);

        service.execute(staffId, newDepartmentId);

        verify(eventPublisher).publishStaffDepartmentChanged(any());
    }

    @Test
    void execute_departmentHeadCannotTransferUntilRemovedOrReassigned() {
        UUID oldDepartmentId = UUID.randomUUID();
        UUID newDepartmentId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Staff doctor = Staff.create("Dr. Alex Morgan", oldDepartmentId, JobTitle.DOCTOR,
                "Cardiology", "MED-1", "0901234567", "alex@example.com", "012345678901");
        Department oldDepartment = Department.create(
                oldDepartmentId, "Clinical", "CLN", DepartmentType.CLINICAL, "Building A");
        oldDepartment.changeHead(doctor);
        Department newDepartment = Department.create(
                newDepartmentId, "Surgery", "SUR", DepartmentType.CLINICAL, "Building B");
        when(staffRepository.findByIdForUpdate(staffId)).thenReturn(Optional.of(doctor));
        when(departmentRepository.findByIdForUpdate(newDepartmentId)).thenReturn(Optional.of(newDepartment));
        when(departmentRepository.findByIdForUpdate(oldDepartmentId)).thenReturn(Optional.of(oldDepartment));

        ChangeStaffDepartmentService service = new ChangeStaffDepartmentService(
                staffRepository, departmentRepository, eventPublisher);

        assertThrows(InvalidDepartmentHeadException.class,
                () -> service.execute(staffId, newDepartmentId));

        verify(staffRepository, never()).save(any());
        verify(eventPublisher, never()).publishStaffDepartmentChanged(any());
    }
}
