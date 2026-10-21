package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.in.UpdateAccountStatusUseCase;
import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase;
import com.mediflow.organization.application.port.in.LookupAccountIdentityUseCase;
import com.mediflow.organization.application.dto.response.AccountIdentityLookupDTO;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class AccountIdentityLookupContractTest {

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
    void identityLookup_isAvailableToGatewayService() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(lookupAccountIdentityUseCase.lookup(accountId)).thenReturn(
                new AccountIdentityLookupDTO(true, true, accountId, "ADMIN", null, null, null));
        mockMvc.perform(get("/api/v1/org/accounts/{accountId}/identity", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exists").exists())
                .andExpect(jsonPath("$.data.active").exists());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void identityLookup_deniesHumanAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/org/accounts/{accountId}/identity", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
