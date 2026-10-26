package com.mediflow.billing.web;

import com.mediflow.billing.application.port.in.FindScheduledPaymentQueueUseCase;
import com.mediflow.billing.infrastructure.config.SecurityConfig;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScheduledPaymentQueueController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"mediflow.billing.ledger.enabled=true", "mediflow.billing.scheduled-payments.enabled=true", "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"})
class ScheduledPaymentQueueControllerTest {
    @Autowired MockMvc http;
    @MockBean FindScheduledPaymentQueueUseCase queue;

    @Test @WithMockUser(roles = "CASHIER")
    void filtersAndPagesWithoutLoadingPaymentAuthority() throws Exception {
        UUID patient = UUID.randomUUID();
        when(queue.find(patient, PageQuery.of(1, 5))).thenReturn(PageResult.empty(PageQuery.of(1, 5)));
        http.perform(get("/api/v2/billing/payment-requests").param("patientId", patient.toString()).param("page", "1").param("size", "5"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.number").value(1));
        verify(queue).find(patient, PageQuery.of(1, 5));
    }
    @Test @WithMockUser(roles = "PATIENT")
    void patientCannotBrowseCashierQueue() throws Exception {
        http.perform(get("/api/v2/billing/payment-requests")).andExpect(status().isForbidden());
        verifyNoInteractions(queue);
    }
    @Test @WithMockUser(roles = "CASHIER")
    void malformedPatientDoesNotReachStore() throws Exception {
        http.perform(get("/api/v2/billing/payment-requests").param("patientId", "invalid")).andExpect(status().isBadRequest());
        verifyNoInteractions(queue);
    }
}
