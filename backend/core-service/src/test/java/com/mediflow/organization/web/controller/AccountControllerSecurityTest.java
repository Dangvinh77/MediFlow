package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.in.LookupAccountIdentityUseCase;
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
import org.springframework.dao.DataIntegrityViolationException;
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
    @Test
    @WithMockUser(username = "00000000-0000-4000-8000-000000000002", roles = "ADMIN")
    void confirmedReplacementFlagReachesKeyedAndUnkeyedCommands() throws Exception {
        UUID actor = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID patient = UUID.fromString("00000000-0000-4000-8000-000000000001");
        var account = com.mediflow.organization.domain.model.Account.create(UUID.randomUUID(), "patient.demo", "hash", null, patient, Role.PATIENT);
        when(createAccountUseCase.executeWithIdempotency(actor, "replace-key", "patient.demo", "password", null, patient, Role.PATIENT, true))
                .thenReturn(new CreateAccountUseCase.CreationResult(account, false));
        when(createAccountUseCase.execute("patient.demo", "password", null, patient, Role.PATIENT, true)).thenReturn(account);
        String body = KEYED_BODY.replace("\"role\":\"PATIENT\"", "\"role\":\"PATIENT\",\"replaceActiveAccount\":true");
        mockMvc.perform(post("/api/v1/org/accounts").header("Idempotency-Key", "replace-key")
                .contentType("application/json").content(body)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/org/accounts").contentType("application/json").content(body)).andExpect(status().isCreated());
        org.mockito.Mockito.verify(createAccountUseCase).executeWithIdempotency(actor, "replace-key", "patient.demo", "password", null, patient, Role.PATIENT, true);
        org.mockito.Mockito.verify(createAccountUseCase).execute("patient.demo", "password", null, patient, Role.PATIENT, true);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void confirmedActivationFlagAndOwnerConflictArePreservedOnWire() throws Exception {
        UUID id = UUID.randomUUID();
        var account = com.mediflow.organization.domain.model.Account.create(id, "doctor.detail", "hash", UUID.randomUUID(), null, Role.DOCTOR);
        when(updateAccountStatusUseCase.execute(id, true)).thenThrow(new com.mediflow.common.exception.DuplicateResourceException("ACCOUNT_OWNER_ACTIVE", "previous active"));
        when(updateAccountStatusUseCase.execute(id, true, true)).thenReturn(account);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/org/accounts/" + id + "/status")
                .contentType("application/json").content("{\"isActive\":true}"))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code").value("ACCOUNT_OWNER_ACTIVE"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/org/accounts/" + id + "/status")
                .contentType("application/json").content("{\"isActive\":true,\"replaceActiveAccount\":true}"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(updateAccountStatusUseCase).execute(id, true, true);
    }

    private static final String KEYED_BODY = """
            {"username":"patient.demo","password":"password",
             "patientId":"00000000-0000-4000-8000-000000000001","role":"PATIENT"}
            """;

    @Test
    @WithMockUser(username = "00000000-0000-4000-8000-000000000002", roles = "ADMIN")
    void keyedCreateUsesSignedActorAndReplaysSameAccount() throws Exception {
        UUID actor = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID patient = UUID.fromString("00000000-0000-4000-8000-000000000001");
        var account = com.mediflow.organization.domain.model.Account.create(
                UUID.randomUUID(), "patient.demo", "hash", null, patient, Role.PATIENT);
        when(createAccountUseCase.executeWithIdempotency(actor, "account-key", "patient.demo", "password",
                null, patient, Role.PATIENT))
                .thenReturn(new CreateAccountUseCase.CreationResult(account, false))
                .thenReturn(new CreateAccountUseCase.CreationResult(account, true));
        mockMvc.perform(post("/api/v1/org/accounts").header("Idempotency-Key", "account-key")
                        .contentType("application/json").content(KEYED_BODY))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Idempotency-Replayed", "false"));
        mockMvc.perform(post("/api/v1/org/accounts").header("Idempotency-Key", "account-key")
                        .contentType("application/json").content(KEYED_BODY))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Idempotency-Replayed", "true"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.accountId").value(account.getAccountId().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = "00000000-0000-4000-8000-000000000002", roles = "ADMIN")
    void keyedCreateRejectsMalformedKey() throws Exception {
        mockMvc.perform(post("/api/v1/org/accounts").header("Idempotency-Key", " ")
                        .contentType("application/json").content(KEYED_BODY))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(createAccountUseCase);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void keyedCreateStillRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/org/accounts").header("Idempotency-Key", "account-key")
                        .contentType("application/json").content(KEYED_BODY))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(createAccountUseCase);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateAccountUseCase createAccountUseCase;

    @MockBean
    private UpdateAccountStatusUseCase updateAccountStatusUseCase;

    @MockBean
    private VerifyCredentialsUseCase verifyCredentialsUseCase;

    @MockBean
    private LookupAccountIdentityUseCase lookupAccountIdentityUseCase;

    @Test
    @WithMockUser(username = "gateway", authorities = "ROLE_SYSTEM_SERVICE")
    void verify_systemRole_isAllowed() throws Exception {
        when(verifyCredentialsUseCase.execute("admin", "password"))
                .thenReturn(new VerifyCredentialsUseCase.VerifiedAccount(
                        UUID.randomUUID(),
                        null,
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
    @WithMockUser(username = "gateway", authorities = "ROLE_SYSTEM_SERVICE")
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

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_duplicateUsername_returnsStableConflictCode() throws Exception {
        when(createAccountUseCase.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key violates uk_account_username"));

        mockMvc.perform(post("/api/v1/org/accounts")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "password",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.error.code").value("ACCOUNT_USERNAME_DUPLICATE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.error.message").value("Account username already exists"));
    }
}
