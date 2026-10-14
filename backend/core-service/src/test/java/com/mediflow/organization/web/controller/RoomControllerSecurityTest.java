package com.mediflow.organization.web.controller;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.organization.application.dto.response.RoomLookupDTO;
import com.mediflow.organization.application.port.in.LookupRoomUseCase;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.organization.infrastructure.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class RoomControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LookupRoomUseCase lookupRoomUseCase;

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_activeRoom_returnsStableProjectionAndCorrelation() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(lookupRoomUseCase.lookup(roomId)).thenReturn(
                new RoomLookupDTO(true, true, roomId, departmentId, "OR-01", "OPERATING_ROOM"));

        mockMvc.perform(get("/api/v1/org/rooms/{id}/lookup", roomId)
                        .header(JwtClaims.HEADER_CORRELATION_ID,
                                "33333333-3333-3333-3333-333333333333"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.data.roomName").value("OR-01"))
                .andExpect(jsonPath("$.data.roomType").value("OPERATING_ROOM"))
                .andExpect(jsonPath("$.correlationId")
                        .value("33333333-3333-3333-3333-333333333333"))
                .andExpect(header().string(JwtClaims.HEADER_CORRELATION_ID,
                        "33333333-3333-3333-3333-333333333333"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_missingRoom_echoesRequestedId() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(lookupRoomUseCase.lookup(roomId)).thenReturn(RoomLookupDTO.missing(roomId));

        mockMvc.perform(get("/api/v1/org/rooms/{id}/lookup", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data.departmentId").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_malformedUuid_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/org/rooms/not-a-uuid/lookup"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_UUID"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void lookup_humanRole_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/org/rooms/{id}/lookup", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_persistenceFailure_returnsUnavailable() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(lookupRoomUseCase.lookup(roomId))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        mockMvc.perform(get("/api/v1/org/rooms/{id}/lookup", roomId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("ORG_LOOKUP_UNAVAILABLE"));
    }
}
