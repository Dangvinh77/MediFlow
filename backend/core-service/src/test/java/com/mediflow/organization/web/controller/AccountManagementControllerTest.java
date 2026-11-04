package com.mediflow.organization.web.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.port.in.ManageAccountsUseCase;
import com.mediflow.organization.application.dto.response.MailDeliveryDTO;
import com.mediflow.organization.application.service.SecurityAccountOperationsService;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.organization.infrastructure.web.CorrelationIdFilter;
import com.mediflow.organization.domain.model.Role;

@WebMvcTest(AccountManagementController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class AccountManagementControllerTest {
    @Test @WithMockUser(roles = "ADMIN")
    void adminDetailContainsAccountMetadataAndNeverPasswordHash() throws Exception {
        var account = com.mediflow.organization.domain.model.Account.create(UUID.fromString(ACTOR),
                "admin.detail", "secret-hash", UUID.randomUUID(), null, Role.ADMIN);
        when(accounts.getById(UUID.fromString(ACTOR))).thenReturn(account);
        mvc.perform(get("/api/v1/org/accounts/" + ACTOR))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.username").value("admin.detail"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test @WithMockUser(roles = "DOCTOR")
    void nonAdminCannotReadAccountDetail() throws Exception {
        mvc.perform(get("/api/v1/org/accounts/" + ACTOR)).andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
    }
    static final String ACTOR = "22222222-2222-2222-2222-222222222222";
    @Autowired MockMvc mvc;
    @MockBean ManageAccountsUseCase accounts;
    @MockBean SecurityAccountOperationsService securityOperations;
    @Test @WithMockUser(roles = "DOCTOR")
    void doctorCannotListAccountsOrResetOtherPasswords() throws Exception {
        mvc.perform(get("/api/v1/org/accounts")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/org/accounts/" + ACTOR + "/password")
                .header("Idempotency-Key", "reset-key").contentType("application/json")
                .content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/org/accounts/" + ACTOR + "/email").contentType("application/json")
                .content("{\"email\":\"new@example.test\",\"reason\":\"correction\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/org/accounts/" + ACTOR + "/mail-delivery"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/org/accounts/" + ACTOR + "/mail-delivery/"
                        + "33333333-3333-4333-8333-333333333333/retry"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
        verifyNoInteractions(securityOperations);
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminCanSearchWithExactFilters() throws Exception {
        when(accounts.search("doc", Role.DOCTOR, false, PageQuery.of(2, 20))).thenReturn(PageResult.empty(PageQuery.of(2, 20)));
        mvc.perform(get("/api/v1/org/accounts?keyword=doc&role=DOCTOR&active=false&page=2"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.number").value(2));
    }
    @Test @WithMockUser(username = ACTOR, roles = "PATIENT")
    void selfPasswordUsesAuthenticatedAccountNotClientSuppliedIdentity() throws Exception {
        mvc.perform(put("/api/v1/org/accounts/me/password").contentType("application/json")
                .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        verify(accounts).changePassword(UUID.fromString(ACTOR), "old-password", "new-password");
    }
    @Test @WithMockUser(username = ACTOR, roles = "ADMIN")
    void adminResetAndValidation() throws Exception {
        UUID messageId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        when(securityOperations.resetPassword(UUID.fromString(ACTOR), UUID.fromString(ACTOR), "reset-key"))
                .thenReturn(new MailDeliveryDTO(messageId, MailDeliveryDTO.Status.PENDING));
        mvc.perform(put("/api/v1/org/accounts/" + ACTOR + "/password")
                        .header("Idempotency-Key", "reset-key").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.messageId").value(messageId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        verify(securityOperations).resetPassword(UUID.fromString(ACTOR), UUID.fromString(ACTOR), "reset-key");

        // The previous API let administrators choose and submit the password. That body is now rejected.
        mvc.perform(put("/api/v1/org/accounts/" + ACTOR + "/password")
                        .header("Idempotency-Key", "legacy-key").contentType("application/json")
                        .content("{\"newPassword\":\"caller-chosen-password\"}"))
                .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(securityOperations);
        verifyNoInteractions(accounts);
    }
    @Test void anonymousCannotListOrChangePassword() throws Exception {
        mvc.perform(get("/api/v1/org/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/org/accounts/me/password").contentType("application/json")
                .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isUnauthorized());
    }
}
