package com.mediflow.billing.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.billing.application.dto.request.CreateInvoiceRequest;
import com.mediflow.billing.application.dto.request.PayInvoiceRequest;
import com.mediflow.billing.application.dto.response.FeeDTO;
import com.mediflow.billing.application.dto.response.InvoiceDTO;
import com.mediflow.billing.application.dto.response.PaymentResultDTO;
import com.mediflow.billing.application.port.in.ManageInvoiceUseCase;
import com.mediflow.billing.domain.model.FeeType;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.domain.model.SagaStatus;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-slice test cho {@link InvoiceController} — HTTP shape + phân quyền, use case bị mock. */
@WebMvcTest(InvoiceController.class)
@Import(InvoiceControllerTest.MethodSecurityConfiguration.class)
class InvoiceControllerTest {

    private static final String BASE_PATH = "/api/v1/billing";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ManageInvoiceUseCase manageInvoiceUseCase;

    @Test
    @WithMockUser(roles = "CASHIER")
    void getById_returnsInvoiceEnvelope() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        when(manageInvoiceUseCase.getById(invoiceId)).thenReturn(invoiceDto(invoiceId));

        mockMvc.perform(get(BASE_PATH + "/invoices/{id}", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.invoiceId").value(invoiceId.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void byPatient_returnsPagedResult() throws Exception {
        UUID patientId = UUID.randomUUID();
        PageQuery defaultPage = PageQuery.of(null, null);
        when(manageInvoiceUseCase.byPatient(eq(patientId), eq(defaultPage)))
                .thenReturn(PageResult.of(List.of(invoiceDto(UUID.randomUUID())), 1, 0, 20));

        mockMvc.perform(get(BASE_PATH + "/patient/{patientId}", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void create_returns201AndLocation() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        CreateInvoiceRequest request = new CreateInvoiceRequest(UUID.randomUUID(), LocalDate.now());
        when(manageInvoiceUseCase.create(any(CreateInvoiceRequest.class))).thenReturn(invoiceDto(invoiceId));

        mockMvc.perform(post(BASE_PATH + "/invoices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/billing/invoices/" + invoiceId))
                .andExpect(jsonPath("$.data.invoiceId").value(invoiceId.toString()));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void create_missingPatientId_returns400WithFieldDetails() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/invoices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());

        verifyNoInteractions(manageInvoiceUseCase);
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void pay_returnsPaymentResult() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        PayInvoiceRequest request = new PayInvoiceRequest(PaymentMethod.CASH);
        PaymentResultDTO result = new PaymentResultDTO(
                invoiceId, true, BigDecimal.valueOf(150000), PaymentMethod.CASH, Instant.now(), SagaStatus.NONE);
        when(manageInvoiceUseCase.pay(eq(invoiceId), any(PayInvoiceRequest.class))).thenReturn(result);

        mockMvc.perform(put(BASE_PATH + "/invoices/{id}/pay", invoiceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        verify(manageInvoiceUseCase).pay(eq(invoiceId), any(PayInvoiceRequest.class));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_forbiddenRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/invoices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateInvoiceRequest(UUID.randomUUID(), LocalDate.now()))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(manageInvoiceUseCase);
    }

    private InvoiceDTO invoiceDto(UUID invoiceId) {
        FeeDTO fee = new FeeDTO(UUID.randomUUID(), FeeType.EXAM, UUID.randomUUID(), LocalDate.now(),
                BigDecimal.valueOf(150000), false);
        return new InvoiceDTO(invoiceId, UUID.randomUUID(), LocalDate.now(), BigDecimal.valueOf(150000),
                false, null, null, SagaStatus.NONE, null, List.of(fee));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
