package com.mediflow.lab.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.lab.application.dto.response.LabWorklistItemDTO;
import com.mediflow.lab.application.service.LabWorklistService;

@WebMvcTest(LabWorklistController.class)
@Import(LabWorklistControllerTest.MethodSecurity.class)
class LabWorklistControllerTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean LabWorklistService service;
    @MockBean com.mediflow.lab.infrastructure.correlation.ThreadLocalCorrelationIdProvider correlation;

    @BeforeEach void setup() {
        when(correlation.currentOrCreate()).thenReturn(UUID.randomUUID());
    }

    @ParameterizedTest @ValueSource(strings = {"DOCTOR", "NURSE", "PATIENT"})
    void onlyLaboratoryStaffSeeTheWorklist(String role) throws Exception {
        mvc.perform(get("/api/v2/lab/worklist").with(user("u").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void labTechSeesOverdueFlag() throws Exception {
        when(service.worklist()).thenReturn(List.of(new LabWorklistItemDTO(UUID.randomUUID(), UUID.randomUUID(),
                "Glucose", "IN_PROGRESS", "GLU", "Glucose máu", "BIOCHEMISTRY", "ACCEPTED", "DRAFT", 1,
                Instant.parse("2026-11-11T05:00:00Z"), true)));
        mvc.perform(get("/api/v2/lab/worklist").with(user("u").roles("LAB_TECH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].overdue").value(true))
                .andExpect(jsonPath("$.data[0].specimenStatus").value("ACCEPTED"));
    }
}
