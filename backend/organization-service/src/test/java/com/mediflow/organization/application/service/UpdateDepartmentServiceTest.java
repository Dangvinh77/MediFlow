package com.mediflow.organization.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.DepartmentHasActiveStaffException;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.InvalidDepartmentException;
import com.mediflow.organization.domain.exception.InvalidDepartmentHeadException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

@ExtendWith(MockitoExtension.class)
class UpdateDepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private StaffRepository staffRepository;

    private UpdateDepartmentService service;

    @BeforeEach
    void setUp() {
        service = new UpdateDepartmentService(departmentRepository, staffRepository);
    }

    @Test
    void execute_validData_updatesDepartmentWithoutUnsupportedUpdateEvent() {
        UUID departmentId = UUID.randomUUID();
        Department department = department(
                departmentId, "Old Name", "OLD", DepartmentType.CLINICAL);
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(departmentRepository.save(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Department result = service.execute(
                departmentId, "New Name", DepartmentType.ADMINISTRATIVE, "Building B", null, null);

        assertEquals("New Name", result.getDepartmentName());
        assertEquals("OLD", result.getAbbreviation());
        assertEquals(DepartmentType.ADMINISTRATIVE, result.getDepartmentType());
        assertEquals("Building B", result.getLocation());
        verify(staffRepository, never()).findById(any());
    }

    @Test
    void execute_unknownDepartment_throwsNotFoundException() {
        UUID departmentId = UUID.randomUUID();
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.empty());

        assertThrows(DepartmentNotFoundException.class, () -> service.execute(
                departmentId, "New Name", DepartmentType.CLINICAL, "Building B", null, null));
    }

    @Test
    void execute_blankName_throwsInvalidDepartmentException() {
        UUID departmentId = UUID.randomUUID();
        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(department(departmentId, "Old Name", "OLD",
                        DepartmentType.CLINICAL)));

        assertThrows(InvalidDepartmentException.class, () -> service.execute(
                departmentId, " ", DepartmentType.CLINICAL, "Building B", null, null));
    }

    @Test
    void execute_assignsDoctorFromSameDepartmentAsHead() {
        UUID departmentId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Department department = department(
                departmentId, "Clinical", "CLN", DepartmentType.CLINICAL);
        Staff doctor = Staff.reconstitute(
                staffId, "Dr. One", departmentId, JobTitle.DOCTOR, "Cardiology", "MED-1",
                "0901234567", "doctor@example.com", true, Instant.now(), Instant.now());
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(doctor));
        when(departmentRepository.save(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Department result = service.execute(
                departmentId, "Clinical", DepartmentType.CLINICAL, "Building A", staffId, null);

        assertEquals(staffId, result.getDepartmentHeadId());
    }

    @Test
    void execute_headFromOtherDepartment_throwsBusinessRule() {
        UUID departmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Department department = department(
                departmentId, "Clinical", "CLN", DepartmentType.CLINICAL);
        Staff doctor = Staff.reconstitute(
                staffId, "Dr. One", otherDepartmentId, JobTitle.DOCTOR, "Cardiology", "MED-1",
                "0901234567", "doctor@example.com", true, Instant.now(), Instant.now());
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(staffRepository.findById(staffId)).thenReturn(Optional.of(doctor));

        assertThrows(InvalidDepartmentHeadException.class, () -> service.execute(
                departmentId, "Clinical", DepartmentType.CLINICAL, "Building A", staffId, null));
    }

    @Test
    void execute_deactivateWithActiveStaff_throwsBusinessRule() {
        UUID departmentId = UUID.randomUUID();
        Department department = department(
                departmentId, "Clinical", "CLN", DepartmentType.CLINICAL);
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(staffRepository.existsByDepartmentIdAndActiveTrue(departmentId)).thenReturn(true);

        assertThrows(DepartmentHasActiveStaffException.class, () -> service.execute(
                departmentId, "Clinical", DepartmentType.CLINICAL, "Building A", null, false));
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void execute_deactivateWithoutActiveStaff_savesInactiveDepartment() {
        UUID departmentId = UUID.randomUUID();
        Department department = department(
                departmentId, "Clinical", "CLN", DepartmentType.CLINICAL);
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(staffRepository.existsByDepartmentIdAndActiveTrue(departmentId)).thenReturn(false);
        when(departmentRepository.save(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Department result = service.execute(
                departmentId, "Clinical", DepartmentType.CLINICAL, "Building A", null, false);

        assertEquals(false, result.isActive());
    }

    private Department department(
            UUID id, String name, String abbreviation, DepartmentType type) {
        return Department.create(id, name, abbreviation, type, "Building A");
    }
}
