package com.mediflow.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mediflow.organization.application.dto.response.StaffIdentityLookupDTO;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

class LookupStaffIdentityServiceTest {

    private final StaffRepository repository = mock(StaffRepository.class);
    private final LookupStaffIdentityService service = new LookupStaffIdentityService(repository);

    @Test
    void existingActiveStaff_returnsIdentityProjection() {
        UUID departmentId = UUID.randomUUID();
        Staff staff = staff(departmentId, true);
        when(repository.findById(staff.getStaffId())).thenReturn(Optional.of(staff));

        assertThat(service.lookup(staff.getStaffId()))
                .isEqualTo(new StaffIdentityLookupDTO(true, true, "NURSE", departmentId,
                        List.of("OR_NURSE")));
        verify(repository).findById(staff.getStaffId());
    }

    @Test
    void existingInactiveStaff_isReturnedAsInactive() {
        Staff staff = staff(UUID.randomUUID(), false);
        when(repository.findById(staff.getStaffId())).thenReturn(Optional.of(staff));

        assertThat(service.lookup(staff.getStaffId()).active()).isFalse();
    }

    @Test
    void missingStaff_isConfirmedAbsence() {
        UUID staffId = UUID.randomUUID();
        when(repository.findById(staffId)).thenReturn(Optional.empty());

        assertThat(service.lookup(staffId)).isEqualTo(StaffIdentityLookupDTO.missing());
    }

    private Staff staff(UUID departmentId, boolean active) {
        return Staff.reconstitute(
                UUID.randomUUID(), "Test Staff", departmentId, JobTitle.NURSE,
                null, null, null, null, active, Instant.now(), Instant.now());
    }
}
