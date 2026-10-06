package com.mediflow.billing.web;

import java.util.UUID;
import com.mediflow.billing.application.port.in.LookupFinancialClearanceUseCase;
import com.mediflow.billing.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinancialClearanceLookupController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties={"mediflow.billing.clearance-lookup.enabled=true","mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"})
class FinancialClearanceLookupControllerTest {
    @Autowired MockMvc http;
    @MockBean LookupFinancialClearanceUseCase lookup;

    @Test @WithMockUser(roles="SYSTEM")
    void lookup_storageFailure_returnsCorrelated503WithoutSqlDetailsOrConfirmedAbsence() throws Exception {
        UUID id=UUID.randomUUID();
        when(lookup.lookup(id)).thenThrow(new DataAccessResourceFailureException("private SQL connection detail"));
        http.perform(get("/api/v1/billing/financial-clearances/{id}/lookup",id).header("X-Correlation-Id","web-trace"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("X-Correlation-Id","web-trace"))
                .andExpect(jsonPath("$.correlationId").value("web-trace")).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BILLING_CLEARANCE_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
    }
    @Test @WithMockUser(roles="SYSTEM")
    void lookup_missingOrBlankCorrelation_returns400WithoutCallingAuthority() throws Exception {
        var request=get("/api/v1/billing/financial-clearances/{id}/lookup",UUID.randomUUID());
        http.perform(request).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/billing/financial-clearances/{id}/lookup",UUID.randomUUID()).header("X-Correlation-Id"," "))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(lookup);
    }
    @Test @WithMockUser(roles="CASHIER")
    void lookup_humanRole_isForbiddenByEndpoint() throws Exception {
        http.perform(get("/api/v1/billing/financial-clearances/{id}/lookup",UUID.randomUUID()).header("X-Correlation-Id","web-trace"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lookup);
    }
}
