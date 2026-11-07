package com.mediflow.organization.web.controller;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.organization.application.dto.response.RoomLookupDTO;
import com.mediflow.organization.application.port.in.LookupRoomUseCase;
import com.mediflow.organization.application.port.in.ManageRoomsUseCase;
import com.mediflow.organization.domain.model.Room;
import com.mediflow.organization.domain.model.RoomType;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @MockBean
    private ManageRoomsUseCase manageRooms;

    private static final String CREATE_BODY = """
            {"departmentId":"%s","roomType":"CONSULTATION","roomNumber":"101",
             "roomName":"Phòng khám Nội 1","zone":"Khu A","location":"Tầng 1, Khu A"}
            """;

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_admin_returnsWorkplaceRoom() throws Exception {
        UUID departmentId = UUID.randomUUID();
        Room room = Room.create(UUID.randomUUID(), departmentId, RoomType.CONSULTATION, "101",
                "Phòng khám Nội 1", "Khu A", "Tầng 1, Khu A");
        when(manageRooms.create(any())).thenReturn(room);

        mockMvc.perform(post("/api/v1/org/rooms").contentType("application/json")
                        .content(CREATE_BODY.formatted(departmentId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/org/rooms/" + room.getRoomId()))
                .andExpect(jsonPath("$.data.roomType").value("CONSULTATION"))
                .andExpect(jsonPath("$.data.roomNumber").value("101"))
                .andExpect(jsonPath("$.data.zone").value("Khu A"))
                .andExpect(jsonPath("$.data.location").value("Tầng 1, Khu A"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_missingZone_isRejectedBeforeTheUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/org/rooms").contentType("application/json")
                        .content("""
                                {"departmentId":"%s","roomType":"CONSULTATION","roomNumber":"101",
                                 "roomName":"Phòng khám","location":"Tầng 1"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(manageRooms);
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void mutations_nonAdmin_areForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/org/rooms").contentType("application/json")
                        .content(CREATE_BODY.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/org/rooms/{id}", id).contentType("application/json")
                        .content("{\"roomNumber\":\"1\",\"roomName\":\"P\",\"zone\":\"A\",\"location\":\"T1\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/org/rooms/{id}/status", id).contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(manageRooms);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void list_staff_readsRoomsByDepartment() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(manageRooms.list(eq(departmentId), isNull(), eq(true))).thenReturn(List.of(
                Room.create(UUID.randomUUID(), departmentId, RoomType.CONSULTATION, "101", "Phòng khám 1", "Khu A", "Tầng 1")));

        mockMvc.perform(get("/api/v1/org/rooms").param("departmentId", departmentId.toString())
                        .param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].roomNumber").value("101"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void list_unrelatedRole_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/org/rooms")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_activeRoom_returnsStableProjectionAndCorrelation() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(lookupRoomUseCase.lookup(roomId)).thenReturn(
                new RoomLookupDTO(true, true, roomId, departmentId, "Phòng khám 1", "CONSULTATION"));

        mockMvc.perform(get("/api/v1/org/rooms/{id}/lookup", roomId)
                        .header(JwtClaims.HEADER_CORRELATION_ID,
                                "33333333-3333-3333-3333-333333333333"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.data.roomName").value("Phòng khám 1"))
                .andExpect(jsonPath("$.data.roomType").value("CONSULTATION"))
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
