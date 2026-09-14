package com.mediflow.billing.web;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.billing.application.dto.request.PayInvoiceRequest;
import com.mediflow.billing.application.port.in.ManageInvoiceUseCase;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.exception.InvoiceNotFoundException;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Xác nhận {@link GlobalExceptionHandler} map đúng mã lỗi billing sang HTTP status. */
@WebMvcTest(InvoiceController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class GlobalExceptionHandlerTest {

    private static final String BASE_PATH = "/api/v1/billing";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManageInvoiceUseCase manageInvoiceUseCase;

    @Test
    @WithMockUser(roles = "CASHIER")
    void missingInvoice_returns404Envelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(manageInvoiceUseCase.getById(id))
                .thenThrow(new InvoiceNotFoundException("Không tìm thấy hóa đơn id=" + id));

        mockMvc.perform(get(BASE_PATH + "/invoices/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVOICE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void alreadyPaidInvoice_returns422Envelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(manageInvoiceUseCase.pay(eq(id), any(PayInvoiceRequest.class)))
                .thenThrow(new BillingRuleException("BILLING_ALREADY_PAID", "Hóa đơn này đã được thanh toán"));

        mockMvc.perform(put(BASE_PATH + "/invoices/{id}/pay", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BILLING_ALREADY_PAID"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void invalidPaymentMethod_returns400Envelope() throws Exception {
        mockMvc.perform(put(BASE_PATH + "/invoices/{id}/pay", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"BITCOIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void byPatient_nonNumericPage_returns400Envelope() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/patient/{patientId}", UUID.randomUUID())
                        .param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
