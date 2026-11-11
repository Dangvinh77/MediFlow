package com.mediflow.lab.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.lab.application.service.LabReportApplicationService;
import com.mediflow.lab.domain.exception.LabRuleException;

@WebMvcTest(LabReportController.class)
@Import(LabReportControllerTest.MethodSecurity.class)
class LabReportControllerTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean LabReportApplicationService service;
    @MockBean com.mediflow.lab.application.service.LabReportReleaseService release;
    @MockBean com.mediflow.lab.infrastructure.correlation.ThreadLocalCorrelationIdProvider correlation;
    final UUID id = UUID.randomUUID();
    static final String DRAFT = "{\"draftRevision\":0,\"values\":[{\"fieldCode\":\"GLU\",\"value\":\"5.2\"}]}";

    @BeforeEach void setup() {
        when(correlation.currentOrCreate()).thenReturn(UUID.randomUUID());
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE", "PATIENT"})
    void onlyLabTechsWriteReports(String role) throws Exception {
        var user = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("u").roles(role);
        mvc.perform(put("/api/v2/lab/" + id + "/report/draft").with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v2/lab/" + id + "/report/submission").with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"draftRevision\":1}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v2/lab/" + id + "/report/return").with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE", "PATIENT"})
    void onlyLabTechsRelease(String role) throws Exception {
        var user = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("u").roles(role);
        mvc.perform(post("/api/v2/lab/" + id + "/report/release").with(user).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(release);
    }

    @Test @WithMockUser(roles = "LAB_TECH")
    void deniedReleaseIsForbidden() throws Exception {
        when(release.release(id)).thenThrow(new com.mediflow.common.exception.ForbiddenOperationException(
                "LAB_RELEASE_DENIED", "denied"));
        mvc.perform(post("/api/v2/lab/" + id + "/report/release").with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("LAB_RELEASE_DENIED"));
    }

    @Test @WithMockUser(roles = "LAB_TECH")
    void staleDraftIsAConflict() throws Exception {
        when(service.saveDraft(eq(id), any())).thenThrow(new DuplicateResourceException("LAB_REPORT_STALE", "stale"));
        mvc.perform(put("/api/v2/lab/" + id + "/report/draft").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("LAB_REPORT_STALE"));
    }

    @Test @WithMockUser(roles = "LAB_TECH")
    void invalidSubmissionIsUnprocessable() throws Exception {
        when(service.submit(eq(id), any())).thenThrow(new LabRuleException("LAB_REPORT_INVALID", "GLU: REQUIRED_MISSING"));
        mvc.perform(post("/api/v2/lab/" + id + "/report/submission").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"draftRevision\":1}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("LAB_REPORT_INVALID"));
    }

    @Test @WithMockUser(roles = "LAB_TECH")
    void returnWithoutReasonIsRejectedAtTheBoundary() throws Exception {
        mvc.perform(post("/api/v2/lab/" + id + "/report/return").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }
}
