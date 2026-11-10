package com.mediflow.billing.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.billing.application.port.in.ListRegistrationSettlementsUseCase;
import com.mediflow.billing.infrastructure.config.SecurityConfig;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegistrationSettlementController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"mediflow.billing.ledger.enabled=true", "mediflow.billing.scheduled-payments.enabled=true",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"})
class RegistrationSettlementControllerTest {
    private static final String PATH = "/api/v2/billing/registration-settlements";
    @Autowired MockMvc http;
    @MockBean ListRegistrationSettlementsUseCase settlements;

    @Test @WithMockUser(roles = "CASHIER")
    void cashierAndAdminOnly() throws Exception {
        when(settlements.list("REFUND_DUE", PageQuery.of(0, 20))).thenReturn(PageResult.empty(PageQuery.of(0, 20)));
        http.perform(get(PATH).param("kind", "REFUND_DUE").param("page", "0").param("size", "20")).andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminMayRead() throws Exception {
        when(settlements.list(null, PageQuery.of(null, null))).thenReturn(PageResult.empty(PageQuery.of(null, null)));
        http.perform(get(PATH)).andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "NURSE")
    void otherRolesAreRefused() throws Exception {
        http.perform(get(PATH)).andExpect(status().isForbidden());
        verifyNoInteractions(settlements);
    }
}
