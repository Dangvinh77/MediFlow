package com.mediflow.organization.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.RoomRepository;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.organization.domain.model.Room;
import com.mediflow.organization.domain.exception.DepartmentHasActiveStaffException;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.InvalidDepartmentException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class UpdateDepartmentServiceTest {

    @Mock private DepartmentRepository departmentRepository;

    @Mock private StaffRepository staffRepository;

    @Mock private RoomRepository roomRepository;

    private UpdateDepartmentService service;

    @BeforeEach
    void setUp() {
        service = new UpdateDepartmentService(departmentRepository, staffRepository, roomRepository);
    }

    @Test
    void execute_departmentWithRooms_keepsItsCategory() {
        UUID departmentId = UUID.randomUUID();
        Department department = department(departmentId, "Khoa Nội", "NOI", DepartmentType.CLINICAL);
        when(departmentRepository.findByIdForUpdate(departmentId)).thenReturn(Optional.of(department));
        when(roomRepository.search(departmentId, null, false)).thenReturn(List.of(Room.create(UUID.randomUUID(),
                departmentId, "CONSULTATION", "101", "Phòng khám Nội 1", "Khu A", "Tầng 1")));

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> service.execute(
                departmentId, "Khoa Nội", DepartmentType.PARACLINICAL, "Tầng 1", null, true));

        assertEquals("DEPARTMENT_TYPE_HAS_ROOMS", error.getCode());
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    void execute_validData_updatesDepartmentWithoutUnsupportedUpdateEvent() {
        UUID departmentId = UUID.randomUUID();
        Department department =
                department(departmentId, "Old Name", "OLD", DepartmentType.CLINICAL);
        when(departmentRepository.findByIdForUpdate(departmentId))
                .thenReturn(Optional.of(department));
        when(departmentRepository.save(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Department result =
                service.execute(
                        departmentId,
                        "New Name",
                        DepartmentType.ADMINISTRATIVE,
                        "Building B",
                        null,
                        null);

        assertEquals("New Name", result.getDepartmentName());
        assertEquals("OLD", result.getAbbreviation());
        assertEquals(DepartmentType.ADMINISTRATIVE, result.getDepartmentType());
        assertEquals("Building A", result.getLocation(), "the location follows the office room, never the request");
        verify(staffRepository, never()).findById(any());
    }

    @Test
    void execute_unknownDepartment_throwsNotFoundException() {
        UUID departmentId = UUID.randomUUID();
        when(departmentRepository.findByIdForUpdate(departmentId)).thenReturn(Optional.empty());

        assertThrows(
                DepartmentNotFoundException.class,
                () ->
                        service.execute(
                                departmentId,
                                "New Name",
                                DepartmentType.CLINICAL,
                                "Building B",
                                null,
                                null));
    }

    @Test
    void execute_blankName_throwsInvalidDepartmentException() {
        UUID departmentId = UUID.randomUUID();
        when(departmentRepository.findByIdForUpdate(departmentId))
                .thenReturn(
                        Optional.of(
                                department(
                                        departmentId, "Old Name", "OLD", DepartmentType.CLINICAL)));

        assertThrows(
                InvalidDepartmentException.class,
                () ->
                        service.execute(
                                departmentId,
                                " ",
                                DepartmentType.CLINICAL,
                                "Building B",
                                null,
                                null));
    }

    @Test
    void execute_rejectsIndependentAppointmentWrite() {
        UUID id = UUID.randomUUID();
        Department d = department(id, "Clinical", "CLN", DepartmentType.CLINICAL);
        when(departmentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(d));
        assertThrows(
                com.mediflow.common.exception.BusinessRuleException.class,
                () ->
                        service.execute(
                                id,
                                "Clinical",
                                DepartmentType.CLINICAL,
                                "A",
                                UUID.randomUUID(),
                                null));
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void execute_rejectsIndependentRevoke() {
        UUID id = UUID.randomUUID(), staffId = UUID.randomUUID();
        Department d = department(id, "Clinical", "CLN", DepartmentType.CLINICAL);
        d.changeHead(
                Staff.reconstitute(
                        staffId,
                        "Doctor",
                        id,
                        JobTitle.DOCTOR,
                        null,
                        "LIC",
                        null,
                        null,
                        true,
                        Instant.now(),
                        Instant.now(),
                        "012345678901"));
        when(departmentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(d));
        assertThrows(
                com.mediflow.common.exception.BusinessRuleException.class,
                () -> service.execute(id, "Clinical", DepartmentType.CLINICAL, "A", null, null));
        assertEquals(staffId, d.getDepartmentHeadId());
    }

    @Test
    void execute_preservesAppointmentProjectionWhileUpdatingDepartmentDetails() {
        UUID id = UUID.randomUUID(), staffId = UUID.randomUUID();
        Department d = department(id, "Clinical", "CLN", DepartmentType.CLINICAL);
        d.changeHead(
                Staff.reconstitute(
                        staffId,
                        "Doctor",
                        id,
                        JobTitle.DOCTOR,
                        null,
                        "LIC",
                        null,
                        null,
                        true,
                        Instant.now(),
                        Instant.now(),
                        "012345678901"));
        when(departmentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(d));
        when(departmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Department result =
                service.execute(
                        id,
                        "New clinical name",
                        DepartmentType.CLINICAL,
                        "New location",
                        staffId,
                        null);

        assertEquals(staffId, result.getDepartmentHeadId());
        assertEquals("New clinical name", result.getDepartmentName());
        verify(staffRepository, never()).findById(any());
    }

    @Test
    void execute_deactivateWithActiveStaff_throwsBusinessRule() {
        UUID departmentId = UUID.randomUUID();
        Department department =
                department(departmentId, "Clinical", "CLN", DepartmentType.CLINICAL);
        when(departmentRepository.findByIdForUpdate(departmentId))
                .thenReturn(Optional.of(department));
        when(staffRepository.existsByDepartmentIdAndActiveTrue(departmentId)).thenReturn(true);

        assertThrows(
                DepartmentHasActiveStaffException.class,
                () ->
                        service.execute(
                                departmentId,
                                "Clinical",
                                DepartmentType.CLINICAL,
                                "Building A",
                                null,
                                false));
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void execute_deactivateWithoutActiveStaff_savesInactiveDepartment() {
        UUID departmentId = UUID.randomUUID();
        Department department =
                department(departmentId, "Clinical", "CLN", DepartmentType.CLINICAL);
        when(departmentRepository.findByIdForUpdate(departmentId))
                .thenReturn(Optional.of(department));
        when(staffRepository.existsByDepartmentIdAndActiveTrue(departmentId)).thenReturn(false);
        when(departmentRepository.save(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Department result =
                service.execute(
                        departmentId,
                        "Clinical",
                        DepartmentType.CLINICAL,
                        "Building A",
                        null,
                        false);

        assertEquals(false, result.isActive());
    }

    private Department department(UUID id, String name, String abbreviation, DepartmentType type) {
        return Department.create(id, name, abbreviation, type, "Building A");
    }
}
