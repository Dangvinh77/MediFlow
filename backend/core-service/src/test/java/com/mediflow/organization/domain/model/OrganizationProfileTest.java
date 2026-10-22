package com.mediflow.organization.domain.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;
import com.mediflow.organization.domain.exception.InvalidDepartmentException;
import com.mediflow.organization.domain.exception.InvalidDepartmentHeadException;
import com.mediflow.organization.domain.exception.InvalidStaffDataException;
import com.mediflow.organization.web.dto.response.StaffResponse;
import static org.assertj.core.api.Assertions.*;

class OrganizationProfileTest {
    @Test
    void optionalProfilesNormalizeBlanksAndValidateContactAndDates() {
        assertThat(new DepartmentProfile(" ", null, "")).isEqualTo(DepartmentProfile.EMPTY);
        assertThat(new DepartmentProfile(" Care ", " 1234 ", " unit@example.test ").description()).isEqualTo("Care");
        assertThatThrownBy(() -> new DepartmentProfile(null, "abc", null)).isInstanceOf(InvalidDepartmentException.class);
        assertThatThrownBy(() -> new DepartmentProfile(null, null, "invalid")).isInstanceOf(InvalidDepartmentException.class);
        assertThatThrownBy(() -> new StaffProfile(LocalDate.now().plusDays(1), null, null, null, null)).isInstanceOf(InvalidStaffDataException.class);
        assertThatThrownBy(() -> new StaffProfile(LocalDate.of(1990,1,1), null, null, LocalDate.of(1980,1,1), null)).isInstanceOf(InvalidStaffDataException.class);
    }

    @Test
    void officeHeadMayBeAdministrativeButMustBelongToTheOfficeAndBeActive() {
        UUID id = UUID.randomUUID();
        Department office = Department.create(id, "Office", "OFF", DepartmentType.ADMINISTRATIVE, null);
        Staff staff = Staff.create("Office Head", id, JobTitle.ADMINISTRATIVE, null, null, null, null, "012345678901");
        office.changeHead(staff);
        assertThat(office.getDepartmentHeadId()).isEqualTo(staff.getStaffId());
        Department other = Department.create(UUID.randomUUID(), "Other", "OTH", DepartmentType.ADMINISTRATIVE, null);
        assertThatThrownBy(() -> other.changeHead(staff)).isInstanceOf(InvalidDepartmentHeadException.class);
        Department clinical = Department.create(id, "Clinical", "CLN", DepartmentType.CLINICAL, null);
        assertThatThrownBy(() -> clinical.changeHead(staff)).isInstanceOf(InvalidDepartmentHeadException.class);
        staff.deactivate();
        assertThatThrownBy(() -> office.changeHead(staff)).isInstanceOf(InvalidDepartmentHeadException.class);
    }

    @Test
    void directoryRedactsNewPersonalFieldsWhileFullResponseContainsThem() {
        StaffProfile profile = new StaffProfile(LocalDate.of(1990,1,1), StaffProfile.Gender.FEMALE, "Private address", LocalDate.of(2020,1,1), "Degree");
        Staff staff = Staff.create("Member", UUID.randomUUID(), JobTitle.ADMINISTRATIVE, null, null, null, null, "012345678901").withProfile(profile);
        StaffResponse full = StaffResponse.from(staff), directory = StaffResponse.directory(staff);
        assertThat(full.address()).isEqualTo("Private address");
        assertThat(full.dateOfBirth()).isEqualTo(profile.dateOfBirth());
        assertThat(directory.dateOfBirth()).isNull();
        assertThat(directory.gender()).isNull();
        assertThat(directory.address()).isNull();
        assertThat(directory.hireDate()).isNull();
        assertThat(directory.qualification()).isEqualTo("Degree");
    }
}
