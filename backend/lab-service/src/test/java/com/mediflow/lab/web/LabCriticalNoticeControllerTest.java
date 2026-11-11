package com.mediflow.lab.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.lab.application.service.LabCriticalNoticeService;

@WebMvcTest(LabCriticalNoticeController.class)
@Import(LabCriticalNoticeControllerTest.MethodSecurity.class)
class LabCriticalNoticeControllerTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean LabCriticalNoticeService service;
    @MockBean com.mediflow.lab.infrastructure.correlation.ThreadLocalCorrelationIdProvider correlation;
    final UUID testId = UUID.randomUUID();
    final UUID noticeId = UUID.randomUUID();

    @BeforeEach void setup() {
        when(correlation.currentOrCreate()).thenReturn(UUID.randomUUID());
    }

    String ack() {
        return "/api/v2/lab/" + testId + "/critical-notices/" + noticeId + "/acknowledgement";
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "NURSE", "LAB_TECH", "PATIENT"})
    void onlyDoctorsAcknowledge(String role) throws Exception {
        mvc.perform(post(ack()).with(user("u").roles(role)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void otherDepartmentDoctorIsForbidden() throws Exception {
        when(service.acknowledge(eq(testId), eq(noticeId))).thenThrow(
                new ForbiddenOperationException("LAB_CRITICAL_NOT_RESPONSIBLE", "x"));
        mvc.perform(post(ack()).with(user("u").roles("DOCTOR")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LAB_CRITICAL_NOT_RESPONSIBLE"));
    }

    @Test void patientsCannotListNotices() throws Exception {
        mvc.perform(get("/api/v2/lab/critical-notices").param("status", "OPEN").with(user("u").roles("PATIENT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
