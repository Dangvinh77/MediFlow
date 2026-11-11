package com.mediflow.organization.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.organization.application.dto.response.LabReleaseAuthority;
import com.mediflow.organization.application.dto.response.RecordReviewAuthority;
import com.mediflow.organization.application.service.StaffCapabilityGrantService;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.organization.infrastructure.web.CorrelationIdFilter;

@WebMvcTest(StaffCapabilityGrantController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class StaffCapabilityGrantControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private StaffCapabilityGrantService service;

    @Test
    void labServiceReadsReleaseAuthority() throws Exception {
        UUID account = UUID.randomUUID();
        when(service.labReleaseAuthority(account))
                .thenReturn(new LabReleaseAuthority(account, true, UUID.randomUUID(), true, true, Instant.now()));

        mockMvc.perform(get("/api/v2/org/lab-release-authority").param("actorAccountId", account.toString())
                        .with(service("lab-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canReleaseLabResults").value(true));
    }

    @Test
    void otherServicesCannotReadReleaseAuthority() throws Exception {
        mockMvc.perform(get("/api/v2/org/lab-release-authority").param("actorAccountId", UUID.randomUUID().toString())
                        .with(service("pharmacy-service")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void authorityEndpointOnlyForClinicalService() throws Exception {
        UUID account = UUID.randomUUID();
        when(service.recordReviewAuthority(account))
                .thenReturn(new RecordReviewAuthority(account, UUID.randomUUID(), true));

        mockMvc.perform(get("/api/v2/org/record-review-authority").param("actorAccountId", account.toString())
                        .with(service("clinical-service")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.actorAccountId").value(account.toString()))
                .andExpect(jsonPath("$.data.canReviewRecords").value(true));

        mockMvc.perform(get("/api/v2/org/record-review-authority").param("actorAccountId", account.toString())
                        .with(service("lab-service")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "LAB_TECH")
    void labTechCannotGrantToThemselves() throws Exception {
        mockMvc.perform(post("/api/v2/org/staff/{id}/capability-grants", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capability\":\"LAB_RESULT_RELEASE\",\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "DOCTOR")
    void doctorCannotRevokeCapabilityGrant() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v2/org/capability-grants/{id}/revocation", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Assignment ended\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
    void blankReasonIsRejected() throws Exception {
        mockMvc.perform(post("/api/v2/org/staff/{id}/capability-grants", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capability\":\"LAB_RESULT_RELEASE\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    /** The JWT filter stores the service name as a plain string principal. */
    private static RequestPostProcessor service(String name) {
        return SecurityMockMvcRequestPostProcessors.authentication(new UsernamePasswordAuthenticationToken(
                name, null, List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_SERVICE"))));
    }
}
