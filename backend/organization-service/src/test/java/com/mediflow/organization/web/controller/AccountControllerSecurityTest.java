package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.in.UpdateAccountStatusUseCase;
import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.domain.exception.InvalidCredentialsException;
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

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class AccountControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateAccountUseCase createAccountUseCase;

    @MockBean
    private UpdateAccountStatusUseCase updateAccountStatusUseCase;

    @MockBean
    private VerifyCredentialsUseCase verifyCredentialsUseCase;

    @Test
    @WithMockUser(roles = "SYSTEM")
    void verify_systemRole_isAllowed() throws Exception {
        when(verifyCredentialsUseCase.execute("admin", "password"))
                .thenReturn(new VerifyCredentialsUseCase.VerifiedAccount(
                        UUID.randomUUID(),
                        null,
                        null,
                        Role.ADMIN));

        mockMvc.perform(post("/api/v1/org/accounts/verify")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void verify_humanRole_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/org/accounts/verify")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SYSTEM")
    void verify_invalidCredentials_returnsInternal422Contract() throws Exception {
        when(verifyCredentialsUseCase.execute("admin", "bad"))
                .thenThrow(new InvalidCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/v1/org/accounts/verify")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "bad"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
    }
}
