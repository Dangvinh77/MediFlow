package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.dto.response.MailDeliveryDTO;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.in.LookupAccountIdentityUseCase;
import com.mediflow.organization.application.port.in.UpdateAccountStatusUseCase;
import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase;
import com.mediflow.organization.application.service.SecurityAccountOperationsService;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.organization.domain.exception.InvalidCredentialsException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class AccountControllerSecurityTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID STAFF = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-4000-8000-000000000003");

    @Autowired MockMvc mvc;
    @MockBean CreateAccountUseCase createAccountUseCase;
    @MockBean UpdateAccountStatusUseCase updateAccountStatusUseCase;
    @MockBean VerifyCredentialsUseCase verifyCredentialsUseCase;
    @MockBean LookupAccountIdentityUseCase lookupAccountIdentityUseCase;
    @MockBean SecurityAccountOperationsService securityOperations;
    @Test
    @WithMockUser(username = "00000000-0000-4000-8000-000000000002", roles = "ADMIN")
    void staffAccountIssuanceIsEmailBasedGeneratedAndNoStore() throws Exception {
        Account account = Account.create(ACCOUNT, "doctor@example.test", "password-hash", STAFF, null, Role.DOCTOR);
        MailDeliveryDTO delivery = new MailDeliveryDTO(UUID.randomUUID(), MailDeliveryDTO.Status.PENDING);
        when(securityOperations.issueStaff(ACTOR, "issue-key", "doctor@example.test", STAFF, Role.DOCTOR, false))
                .thenReturn(new SecurityAccountOperationsService.IssueResult(account, false, delivery));

        mvc.perform(post("/api/v1/org/accounts").header("Idempotency-Key", "issue-key")
                        .contentType("application/json")
                        .content("""
                                {"username":"doctor@example.test","staffId":"00000000-0000-4000-8000-000000000001","role":"DOCTOR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.data.username").value("doctor@example.test"))
                .andExpect(jsonPath("$.data.mailDelivery.messageId").value(delivery.messageId().toString()))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verify(securityOperations).issueStaff(ACTOR, "issue-key", "doctor@example.test", STAFF, Role.DOCTOR, false);
    }

    @Test
    @WithMockUser(username = "00000000-0000-4000-8000-000000000002", roles = "ADMIN")
    void accountIssuanceRejectsCallerChosenPasswordAndMissingIdempotencyKey() throws Exception {
        String body = """
                {"username":"doctor@example.test","staffId":"00000000-0000-4000-8000-000000000001","role":"DOCTOR"}
                """;
        mvc.perform(post("/api/v1/org/accounts").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/org/accounts").header("Idempotency-Key", "issue-key")
                        .contentType("application/json")
                        .content("""
                                {"username":"doctor@example.test","password":"chosen-by-admin","staffId":"00000000-0000-4000-8000-000000000001","role":"DOCTOR"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(securityOperations);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void nonAdminCannotIssueStaffAccount() throws Exception {
        mvc.perform(post("/api/v1/org/accounts").header("Idempotency-Key", "issue-key")
                        .contentType("application/json")
                        .content("""
                                {"username":"doctor@example.test","staffId":"00000000-0000-4000-8000-000000000001","role":"DOCTOR"}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(securityOperations);
    }

    @Test
    @WithMockUser(username = "gateway", authorities = "ROLE_SYSTEM_SERVICE")
    void gatewayCanVerifyCredentials() throws Exception {
        when(verifyCredentialsUseCase.execute("person@example.test", "password"))
                .thenReturn(new VerifyCredentialsUseCase.VerifiedAccount(UUID.randomUUID(), null, null, null, Role.ADMIN));
        mvc.perform(post("/api/v1/org/accounts/verify").contentType("application/json")
                        .content("{\"username\":\"person@example.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void humanRoleCannotVerifyCredentials() throws Exception {
        mvc.perform(post("/api/v1/org/accounts/verify").contentType("application/json")
                        .content("{\"username\":\"person@example.test\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verifyCredentialsUseCase);
    }

    @Test
    @WithMockUser(username = "gateway", authorities = "ROLE_SYSTEM_SERVICE")
    void invalidCredentialsUsesStableAuthError() throws Exception {
        when(verifyCredentialsUseCase.execute("person@example.test", "bad"))
                .thenThrow(new InvalidCredentialsException("Invalid username or password"));
        mvc.perform(post("/api/v1/org/accounts/verify").contentType("application/json")
                        .content("{\"username\":\"person@example.test\",\"password\":\"bad\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
    }
}
