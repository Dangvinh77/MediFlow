package com.mediflow.lab.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.lab.application.dto.response.LabCatalogDTOs.LabCatalogItemDTO;
import com.mediflow.lab.application.service.LabCatalogApplicationService;

@WebMvcTest(LabCatalogController.class)
@Import(LabCatalogControllerTest.MethodSecurity.class)
class LabCatalogControllerTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean LabCatalogApplicationService service;
    @MockBean com.mediflow.lab.infrastructure.correlation.ThreadLocalCorrelationIdProvider correlation;

    @BeforeEach void setup() {
        when(correlation.currentOrCreate()).thenReturn(UUID.randomUUID());
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE", "LAB_TECH"})
    void staffReadTheCatalogueByGroup(String role) throws Exception {
        when(service.services("HEMATOLOGY")).thenReturn(List.of(new LabCatalogItemDTO("CBC", "Công thức máu",
                "HEMATOLOGY", "WHOLE_BLOOD_EDTA", null, 4, "LAB-CBC", "CBC", true)));
        mvc.perform(get("/api/v2/lab/catalog?group=HEMATOLOGY").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("u")
                        .roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].serviceCode").value("CBC"))
                .andExpect(jsonPath("$.data[0].priceCode").value("LAB-CBC"));
    }

    @Test @WithMockUser(roles = "PATIENT")
    void patientsCannotReadTheStaffCatalogue() throws Exception {
        mvc.perform(get("/api/v2/lab/catalog")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v2/lab/" + UUID.randomUUID() + "/template")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "DOCTOR")
    void disabledCatalogueAnswersNotFound() throws Exception {
        when(service.services(null)).thenThrow(new ResourceNotFoundException("LAB_CATALOG_DISABLED", "off"));
        mvc.perform(get("/api/v2/lab/catalog")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LAB_CATALOG_DISABLED"));
    }

    @Test @WithMockUser(roles = "LAB_TECH")
    void readsTheTemplateATestWasCreatedWith() throws Exception {
        UUID testId = UUID.randomUUID();
        mvc.perform(get("/api/v2/lab/" + testId + "/template")).andExpect(status().isOk());
        verify(service).templateOf(testId);
    }
}
