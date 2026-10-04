package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.application.port.in.GetDepartmentUseCase;
import com.mediflow.organization.application.port.in.LookupDepartmentUseCase;
import com.mediflow.organization.application.dto.response.DepartmentLookupDTO;
import com.mediflow.organization.application.port.in.UpdateDepartmentUseCase;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.Department;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(DepartmentController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class DepartmentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateDepartmentUseCase createDepartmentUseCase;

    @MockBean
    private GetDepartmentUseCase getDepartmentUseCase;

    @MockBean
    private LookupDepartmentUseCase lookupDepartmentUseCase;

    @MockBean
    private UpdateDepartmentUseCase updateDepartmentUseCase;

    @Test
    @WithMockUser(roles = "DOCTOR")
    void list_clinicalRole_isAllowed() throws Exception {
        when(getDepartmentUseCase.getAllDepartments()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/org/departments"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void list_patientRole_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/org/departments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_clinicalRole_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/org/departments")
                        .contentType("application/json")
                        .content("""
                                {
                                  "departmentName": "Outpatient",
                                  "abbreviation": "OUT",
                                  "departmentType": "CLINICAL",
                                  "location": "Building A"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_adminRole_isAllowed() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(createDepartmentUseCase.execute(
                "Outpatient", "OUT", DepartmentType.CLINICAL, "Building A"))
                .thenReturn(Department.create(
                        departmentId,
                        "Outpatient",
                        "OUT",
                        DepartmentType.CLINICAL,
                        "Building A"));

        mockMvc.perform(post("/api/v1/org/departments")
                        .contentType("application/json")
                        .content("""
                                {
                                  "departmentName": "Outpatient",
                                  "abbreviation": "OUT",
                                  "departmentType": "CLINICAL",
                                  "location": "Building A"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_systemService_returnsDepartmentProjectionAndCorrelation() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(lookupDepartmentUseCase.lookup(departmentId)).thenReturn(
                new DepartmentLookupDTO(true, true, departmentId, "Outpatient", "CLINICAL"));

        mockMvc.perform(get("/api/v1/org/departments/{id}/lookup", departmentId)
                        .header("X-Correlation-Id", "22222222-2222-2222-2222-222222222222"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.data.departmentName").value("Outpatient"))
                .andExpect(jsonPath("$.data.departmentType").value("CLINICAL"))
                .andExpect(jsonPath("$.correlationId")
                        .value("22222222-2222-2222-2222-222222222222"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_missingDepartment_echoesId() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(lookupDepartmentUseCase.lookup(departmentId))
                .thenReturn(DepartmentLookupDTO.missing(departmentId));

        mockMvc.perform(get("/api/v1/org/departments/{id}/lookup", departmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId.toString()));
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_persistenceFailure_returnsUnavailable() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(lookupDepartmentUseCase.lookup(departmentId))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        mockMvc.perform(get("/api/v1/org/departments/{id}/lookup", departmentId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("ORG_LOOKUP_UNAVAILABLE"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_malformedUuid_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/org/departments/not-a-uuid/lookup"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_UUID"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void lookup_humanRole_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/org/departments/{id}/lookup", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
