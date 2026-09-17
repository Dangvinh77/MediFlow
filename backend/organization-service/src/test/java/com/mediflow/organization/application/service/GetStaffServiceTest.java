package com.mediflow.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.organization.application.dto.response.StaffLookupDTO;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;

class GetStaffServiceTest {

    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final GetStaffService service = new GetStaffService(staffRepository);

    @Test
    void lookup_missingStaff_returnsMissing() {
        UUID staffId = UUID.randomUUID();
        when(staffRepository.findById(staffId)).thenReturn(Optional.empty());

        assertThat(service.lookup(staffId))
                .isEqualTo(StaffLookupDTO.missing());
        verify(staffRepository).findById(staffId);
        verifyNoMoreInteractions(staffRepository);
    }

    @Test
    void lookup_activeDoctor_returnsEligibleWithDepartment() {
        UUID departmentId = UUID.randomUUID();
        Staff staff = staff(departmentId, JobTitle.DOCTOR, true);
        when(staffRepository.findById(staff.getStaffId())).thenReturn(Optional.of(staff));

        assertThat(service.lookup(staff.getStaffId()))
                .isEqualTo(StaffLookupDTO.eligible(departmentId));
    }

    @Test
    void lookup_nonDoctor_returnsIneligibleWithoutDepartment() {
        Staff staff = staff(UUID.randomUUID(), JobTitle.NURSE, true);
        when(staffRepository.findById(staff.getStaffId())).thenReturn(Optional.of(staff));

        assertThat(service.lookup(staff.getStaffId()))
                .isEqualTo(StaffLookupDTO.ineligible());
    }

    @Test
    void lookup_inactiveDoctor_returnsIneligibleWithoutDepartment() {
        Staff staff = staff(UUID.randomUUID(), JobTitle.DOCTOR, false);
        when(staffRepository.findById(staff.getStaffId())).thenReturn(Optional.of(staff));

        assertThat(service.lookup(staff.getStaffId()))
                .isEqualTo(StaffLookupDTO.ineligible());
    }

    private Staff staff(UUID departmentId, JobTitle jobTitle, boolean active) {
        return Staff.reconstitute(
                UUID.randomUUID(),
                "Test Staff",
                departmentId,
                jobTitle,
                null,
                jobTitle == JobTitle.DOCTOR ? "LIC-001" : null,
                null,
                null,
                active,
                Instant.now(),
                Instant.now());
    }
}
