package com.mediflow.lab.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.lab.application.dto.request.AddResultRequest;
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.request.LabResultItem;
import com.mediflow.lab.application.dto.response.LabTestDTO;
import com.mediflow.lab.application.port.in.ManageLabTestUseCase;
import com.mediflow.lab.domain.model.LabTestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LabController.class)
@Import(LabControllerTest.MethodSecurityConfiguration.class)
class LabControllerTest {

    private static final String BASE_PATH = "/api/v1/lab";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ManageLabTestUseCase manageLabTestUseCase;

    @Test
    @WithMockUser(roles = "DOCTOR")
    void getById_returnsLabTestEnvelope() throws Exception {
        UUID testId = UUID.randomUUID();
        when(manageLabTestUseCase.getById(testId)).thenReturn(labTest(testId));

        mockMvc.perform(get(BASE_PATH + "/{id}", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.testId").value(testId.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getByPatient_returnsPatientTests() throws Exception {
        UUID patientId = UUID.randomUUID();
        when(manageLabTestUseCase.byPatient(patientId))
                .thenReturn(List.of(labTest(UUID.randomUUID())));

        mockMvc.perform(get(BASE_PATH + "/patient/{patientId}", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "LAB_TECH")
    void search_passesFiltersAndPageQuery() throws Exception {
        UUID departmentId = UUID.randomUUID();
        PageQuery pageQuery = new PageQuery(1, 10);
        when(manageLabTestUseCase.search(
                departmentId, LabTestStatus.PENDING, pageQuery))
                .thenReturn(PageResult.of(List.of(labTest(UUID.randomUUID())), 12, 1, 10));

        mockMvc.perform(get(BASE_PATH)
                        .param("departmentId", departmentId.toString())
                        .param("status", "PENDING")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(12));

        verify(manageLabTestUseCase)
                .search(departmentId, LabTestStatus.PENDING, pageQuery);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_returns201AndLocation() throws Exception {
        UUID testId = UUID.randomUUID();
        CreateLabRequest request = createRequest();
        when(manageLabTestUseCase.create(any(CreateLabRequest.class)))
                .thenReturn(labTest(testId));

        mockMvc.perform(post(BASE_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE_PATH + "/" + testId))
                .andExpect(jsonPath("$.data.testId").value(testId.toString()));
    }

    @Test
    @WithMockUser(roles = "LAB_TECH")
    void addResults_returnsUpdatedTest() throws Exception {
        UUID testId = UUID.randomUUID();
        AddResultRequest request = new AddResultRequest(
                List.of(new LabResultItem("Glucose", "5.2", "mmol/L", "3.9-6.4")),
                "Bình thường",
                LocalDate.now());
        when(manageLabTestUseCase.addResults(testId, request)).thenReturn(labTest(testId));

        mockMvc.perform(put(BASE_PATH + "/{id}/results", testId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.testId").value(testId.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void changeStatus_delegatesRequestedStatus() throws Exception {
        UUID testId = UUID.randomUUID();
        when(manageLabTestUseCase.changeStatus(testId, LabTestStatus.IN_PROGRESS))
                .thenReturn(labTest(testId));

        mockMvc.perform(put(BASE_PATH + "/{id}/status", testId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        verify(manageLabTestUseCase).changeStatus(testId, LabTestStatus.IN_PROGRESS);
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void create_forbiddenRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(manageLabTestUseCase);
    }

    private CreateLabRequest createRequest() {
        return new CreateLabRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BLOOD_TEST",
                LocalDate.now());
    }

    private LabTestDTO labTest(UUID testId) {
        Instant now = Instant.now();
        return new LabTestDTO(
                testId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BLOOD_TEST",
                LocalDate.now(),
                null,
                LabTestStatus.PENDING,
                null,
                false,
                List.of(),
                now,
                now);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
