package com.mediflow.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.organization.application.dto.response.DepartmentLookupDTO;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;

class LookupDepartmentServiceTest {

    private final DepartmentRepository repository = mock(DepartmentRepository.class);
    private final LookupDepartmentService service = new LookupDepartmentService(repository);

    @Test
    void existingActiveDepartment_returnsIdentityProjection() {
        UUID id = UUID.randomUUID();
        Department department = department(id, true);
        when(repository.findById(id)).thenReturn(Optional.of(department));

        assertThat(service.lookup(id)).isEqualTo(new DepartmentLookupDTO(
                true, true, id, "Outpatient", "CLINICAL"));
    }

    @Test
    void existingInactiveDepartment_preservesInactiveState() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(department(id, false)));

        assertThat(service.lookup(id).active()).isFalse();
    }

    @Test
    void missingDepartment_echoesRequestedId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.lookup(id)).isEqualTo(DepartmentLookupDTO.missing(id));
    }

    private Department department(UUID id, boolean active) {
        return Department.reconstitute(
                id, "Outpatient", "OUT", DepartmentType.CLINICAL, null,
                "Building A", active, Instant.now(), Instant.now());
    }
}
