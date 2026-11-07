package com.mediflow.organization.web.controller;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.organization.application.port.in.ManageRoomTypesUseCase;
import com.mediflow.organization.application.port.in.ManageRoomTypesUseCase.NewRoomType;
import com.mediflow.organization.application.port.in.ManageRoomTypesUseCase.RoomTypeUsage;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.RoomTypeDefinition;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.organization.infrastructure.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomTypeController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class RoomTypeControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManageRoomTypesUseCase types;

    private static final String CREATE_BODY = """
            {"name":"Phòng thủ thuật","description":"Thủ thuật ngoại trú",
             "departmentType":"CLINICAL","singlePerDepartment":false}
            """;

    @Test
    @WithMockUser(roles = "NURSE")
    void list_staffRole_returnsCatalogueWithUsage() throws Exception {
        when(types.list()).thenReturn(List.of(new RoomTypeUsage(RoomTypeDefinition.reconstitute(
                "CONSULTATION", "Phòng khám", null, DepartmentType.CLINICAL, false, true, 10, null, null), 3)));

        mockMvc.perform(get("/api/v1/org/room-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("CONSULTATION"))
                .andExpect(jsonPath("$.data[0].departmentType").value("CLINICAL"))
                .andExpect(jsonPath("$.data[0].schedulable").value(true))
                .andExpect(jsonPath("$.data[0].systemDefined").value(true))
                .andExpect(jsonPath("$.data[0].roomCount").value(3));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_admin_returnsTheNewType() throws Exception {
        when(types.create(any())).thenReturn(new RoomTypeUsage(RoomTypeDefinition.define(
                "Phòng thủ thuật", "Thủ thuật ngoại trú", DepartmentType.CLINICAL, false, 70), 0));

        mockMvc.perform(post("/api/v1/org/room-types").contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/org/room-types/PHONG_THU_THUAT"))
                .andExpect(jsonPath("$.data.code").value("PHONG_THU_THUAT"))
                .andExpect(jsonPath("$.data.singlePerDepartment").value(false))
                .andExpect(jsonPath("$.data.schedulable").value(false));
        verify(types).create(new NewRoomType("Phòng thủ thuật", "Thủ thuật ngoại trú", DepartmentType.CLINICAL, false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withoutCategory_isRejectedBeforeTheUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/org/room-types").contentType("application/json")
                        .content("{\"name\":\"Phòng thủ thuật\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(types);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_typeInUse_isUnprocessable() throws Exception {
        doThrow(new BusinessRuleException("ROOM_TYPE_IN_USE", "Room type is used by existing rooms"))
                .when(types).delete("PHARMACY");

        mockMvc.perform(delete("/api/v1/org/room-types/{code}", "PHARMACY"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ROOM_TYPE_IN_USE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_unusedType_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/org/room-types/{code}", "PHONG_THU_THUAT"))
                .andExpect(status().isNoContent());
        verify(types).delete("PHONG_THU_THUAT");
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void mutations_nonAdmin_areForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/org/room-types").contentType("application/json").content(CREATE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/org/room-types/{code}", "CASHIER").contentType("application/json")
                        .content("{\"name\":\"Quầy thu viện phí\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/org/room-types/{code}", "CASHIER"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(types);
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void list_patient_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/org/room-types")).andExpect(status().isForbidden());
    }
}
