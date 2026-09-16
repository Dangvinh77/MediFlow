package com.mediflow.billing.infrastructure.config;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.billing.application.dto.response.InvoiceDTO;
import com.mediflow.billing.application.port.in.ManageInvoiceUseCase;
import com.mediflow.billing.domain.model.SagaStatus;
import com.mediflow.billing.web.InvoiceController;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManageInvoiceUseCase manageInvoiceUseCase;

    @Test
    void getById_withoutAuthentication_returns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/billing/invoices/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void getById_withForbiddenRole_returns403Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/billing/invoices/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void getById_withAllowedRole_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(manageInvoiceUseCase.getById(id)).thenReturn(new InvoiceDTO(
                id, UUID.randomUUID(), null, null, false, null, null, SagaStatus.NONE, null, java.util.List.of()));

        mockMvc.perform(get("/api/v1/billing/invoices/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
