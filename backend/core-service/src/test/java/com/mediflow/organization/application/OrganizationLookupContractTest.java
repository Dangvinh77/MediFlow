package com.mediflow.organization.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.dto.response.RoomLookupDTO;
import com.mediflow.organization.application.dto.response.StaffIdentityLookupDTO;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class OrganizationLookupContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void roomFixtureDeserializesTheLockedEnvelope() throws Exception {
        ApiResponse<RoomLookupDTO> response = objectMapper.readValue(
                fixture("contracts/organization.room.lookup.json"),
                new TypeReference<ApiResponse<RoomLookupDTO>>() { });

        assertThat(response.success()).isTrue();
        assertThat(response.data().exists()).isTrue();
        assertThat(response.data().active()).isTrue();
        assertThat(response.data().roomType()).isEqualTo("OPERATING_ROOM");
        assertThat(response.data().roomId().toString())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(response.correlationId())
                .isEqualTo("33333333-3333-3333-3333-333333333333");
    }

    @Test
    void staffFixtureDeserializesTheAdditiveTeamRoleProjection() throws Exception {
        ApiResponse<StaffIdentityLookupDTO> response = objectMapper.readValue(
                fixture("contracts/organization.staff.lookup.json"),
                new TypeReference<ApiResponse<StaffIdentityLookupDTO>>() { });

        assertThat(response.data().jobTitle()).isEqualTo("DOCTOR");
        assertThat(response.data().eligibleTeamRoles())
                .containsExactly("PRIMARY_SURGEON", "ASSISTANT_SURGEON", "ANESTHESIOLOGIST");
    }

    private String fixture(String name) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
            assertThat(stream).as("fixture %s", name).isNotNull();
            return new String(stream.readAllBytes());
        }
    }
}
