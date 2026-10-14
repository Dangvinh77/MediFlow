package com.mediflow.organization.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobTitleTeamRoleMappingTest {

    @Test
    void doctor_mapsToAllV1PhysicianTeamRoles() {
        assertThat(JobTitleTeamRoleMapping.rolesFor(JobTitle.DOCTOR))
                .containsExactly("PRIMARY_SURGEON", "ASSISTANT_SURGEON", "ANESTHESIOLOGIST");
    }

    @Test
    void nurse_mapsToOperatingRoomNurse() {
        assertThat(JobTitleTeamRoleMapping.rolesFor(JobTitle.NURSE))
                .containsExactly("OR_NURSE");
    }

    @Test
    void nonClinicalTitles_areNotEligible() {
        assertThat(JobTitleTeamRoleMapping.rolesFor(JobTitle.TECHNICIAN)).isEqualTo(List.of());
        assertThat(JobTitleTeamRoleMapping.rolesFor(JobTitle.MANAGER)).isEqualTo(List.of());
    }
}
