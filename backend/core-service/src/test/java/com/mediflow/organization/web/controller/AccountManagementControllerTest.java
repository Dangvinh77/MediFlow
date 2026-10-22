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
    @Test @WithMockUser(roles = "DOCTOR")
    void doctorCannotListAccountsOrResetOtherPasswords() throws Exception {
        mvc.perform(get("/api/v1/org/accounts")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/org/accounts/" + ACTOR + "/password").contentType("application/json")
                .content("{\"newPassword\":\"new-password\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
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
    @Test @WithMockUser(roles = "ADMIN")
    void adminResetAndValidation() throws Exception {
        mvc.perform(put("/api/v1/org/accounts/" + ACTOR + "/password").contentType("application/json")
                .content("{\"newPassword\":\"new-password\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        verify(accounts).resetPassword(UUID.fromString(ACTOR), "new-password");
        mvc.perform(put("/api/v1/org/accounts/" + ACTOR + "/password").contentType("application/json")
                .content("{\"newPassword\":\"short\"}")).andExpect(status().isBadRequest());
        verifyNoMoreInteractions(accounts);
    }
    @Test void anonymousCannotListOrChangePassword() throws Exception {
        mvc.perform(get("/api/v1/org/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/org/accounts/me/password").contentType("application/json")
                .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isUnauthorized());
    }
}
