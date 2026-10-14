package com.mediflow.organization.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.event.DepartmentCreatedEvent;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;

@ExtendWith(MockitoExtension.class)
class CreateDepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Test
    void execute_duplicateAbbreviation_throwsDuplicate() {
        when(departmentRepository.existsByAbbreviation("CLN")).thenReturn(true);
        CreateDepartmentService service = new CreateDepartmentService(
                departmentRepository, eventPublisher);

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
                () -> service.execute("Clinical", "CLN", DepartmentType.CLINICAL, "Building A"));

        assertEquals("DEPARTMENT_ABBREVIATION_DUPLICATE", exception.getCode());
    }

    @Test
    void execute_validDepartment_publishesEnvelopeEvent() {
        when(departmentRepository.existsByAbbreviation("CLN")).thenReturn(false);
        when(departmentRepository.save(any(Department.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        CreateDepartmentService service = new CreateDepartmentService(
                departmentRepository, eventPublisher);

        Department saved = service.execute(
                "Clinical", "CLN", DepartmentType.CLINICAL, "Building A");

        ArgumentCaptor<DepartmentCreatedEvent> captor =
                ArgumentCaptor.forClass(DepartmentCreatedEvent.class);
        verify(eventPublisher).publishDepartmentCreated(captor.capture());
        DepartmentCreatedEvent event = captor.getValue();
        assertEquals(saved.getDepartmentId(), event.departmentId());
        assertEquals("CLINICAL", event.departmentType());
        assertEquals("Clinical", event.departmentName());
        org.junit.jupiter.api.Assertions.assertNotNull(event.eventId());
        org.junit.jupiter.api.Assertions.assertNotNull(event.occurredAt());
        org.junit.jupiter.api.Assertions.assertNotNull(event.correlationId());
    }
}
