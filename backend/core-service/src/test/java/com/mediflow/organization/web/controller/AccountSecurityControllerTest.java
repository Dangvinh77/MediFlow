package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.dto.response.DemoMailboxConfigDTO;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.service.DemoMailboxQueryService;
import com.mediflow.organization.application.service.SecurityAccountOperationsService;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountSecurityController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class AccountSecurityControllerTest {
    @Autowired MockMvc mvc;
    @MockBean SecurityAccountOperationsService operations;
    @MockBean DemoMailboxQueryService mailbox;
    @MockBean CorrelationIdProvider correlation;

    @BeforeEach
    void setUp() {
        when(correlation.currentOrCreate()).thenReturn(UUID.randomUUID());
    }

    @Test
    @WithMockUser(username = "gateway", authorities = "ROLE_SYSTEM_SERVICE")
    void gatewaySystemServiceCanReachInternalRecoveryAndMailboxRoutes() throws Exception {
        when(mailbox.config()).thenReturn(new DemoMailboxConfigDTO(true));
        when(operations.forgotPassword("person@example.test"))
                .thenReturn(new SecurityAccountOperationsService.ForgotPasswordResult(true));
        mvc.perform(post("/api/v1/org/account-security/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.test\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.accepted").value(true));
        mvc.perform(get("/api/v1/org/account-security/demo-mailbox/config"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.enabled").value(true));
        verify(operations).forgotPassword("person@example.test");
        verify(mailbox).config();
    }

    @Test
    @WithMockUser(username = "clinical-service", authorities = "ROLE_SYSTEM_SERVICE")
    void otherSystemServiceCannotUseRecoveryOrMailboxRoutes() throws Exception {
        mvc.perform(post("/api/v1/org/account-security/reset-password")
                        .contentType("application/json")
                        .content("{\"token\":\"opaque\",\"newPassword\":\"strong-password\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/org/account-security/demo-mailbox/config"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(operations, mailbox);
    }

    @Test
    @WithMockUser(username = "gateway", roles = "ADMIN")
    void humanAdminCannotUseInternalGatewayEndpoints() throws Exception {
        mvc.perform(post("/api/v1/org/account-security/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.test\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(operations, mailbox);
    }

    @Test
    void anonymousCannotUseInternalGatewayEndpoints() throws Exception {
        mvc.perform(get("/api/v1/org/account-security/demo-mailbox/config"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(operations, mailbox);
    }
}
