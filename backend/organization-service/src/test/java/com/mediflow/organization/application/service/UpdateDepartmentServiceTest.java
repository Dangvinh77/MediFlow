package com.mediflow.organization.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.InvalidDepartmentException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;

@ExtendWith(MockitoExtension.class)
class UpdateDepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EventPublisher eventPublisher;

    private UpdateDepartmentService service;

    @BeforeEach
    void setUp() {
        service = new UpdateDepartmentService(
                departmentRepository,
                eventPublisher);
    }

    @Test
    void execute_validData_updatesDepartmentAndPublishesEvent() {
        UUID departmentId = UUID.randomUUID();
        Department department = Department.create(
                departmentId,
                "Old Name",
                "OLD",
                DepartmentType.CLINICAL,
                "Building A");

        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(department));
        when(departmentRepository.save(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Department result = service.execute(
                departmentId,
                "New Name",
                DepartmentType.ADMINISTRATIVE,
                "Building B");

        assertEquals("New Name", result.getDepartmentName());
        assertEquals("OLD", result.getAbbreviation());
        assertEquals(DepartmentType.ADMINISTRATIVE, result.getDepartmentType());
        assertEquals("Building B", result.getLocation());

        ArgumentCaptor<UpdateDepartmentService.DepartmentUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(UpdateDepartmentService.DepartmentUpdatedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        UpdateDepartmentService.DepartmentUpdatedEvent event = eventCaptor.getValue();
        assertEquals(departmentId, event.departmentId());
        assertEquals("New Name", event.departmentName());
        assertEquals("ADMINISTRATIVE", event.departmentType());
    }

    @Test
    void execute_unknownDepartment_throwsNotFoundException() {
        UUID departmentId = UUID.randomUUID();
        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.empty());

        assertThrows(
                DepartmentNotFoundException.class,
                () -> service.execute(
                        departmentId,
                        "New Name",
                        DepartmentType.CLINICAL,
                        "Building B"));
    }

    @Test
    void execute_blankName_throwsInvalidDepartmentException() {
        UUID departmentId = UUID.randomUUID();
        Department department = Department.create(
                departmentId,
                "Old Name",
                "OLD",
                DepartmentType.CLINICAL,
                "Building A");

        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(department));

        assertThrows(
                InvalidDepartmentException.class,
                () -> service.execute(
                        departmentId,
                        " ",
                        DepartmentType.CLINICAL,
                        "Building B"));
    }
}
