package com.mediflow.billing.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.billing.application.dto.response.PaymentRequestDiscoveryDTO;
import com.mediflow.billing.application.port.in.DiscoverPaymentRequestUseCase;
import com.mediflow.billing.domain.exception.LedgerPaymentRequestNotFoundException;
import com.mediflow.billing.infrastructure.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentRequestDiscoveryController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"mediflow.billing.ledger.enabled=true",
        "mediflow.billing.payment-request-discovery.enabled=true",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"})
class PaymentRequestDiscoveryControllerTest {
    @Autowired MockMvc http;
    @MockBean DiscoverPaymentRequestUseCase discovery;

    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID EPISODE_ID = UUID.randomUUID();

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest() {
        return get("/api/v1/billing/payment-requests/by-source")
                .param("purpose", "EXAM").param("sourceId", SOURCE_ID.toString())
                .param("patientId", PATIENT_ID.toString()).param("careEpisodeId", EPISODE_ID.toString());
    }

    private static PaymentRequestDiscoveryDTO sampleDTO() {
        UUID request = UUID.randomUUID();
        return new PaymentRequestDiscoveryDTO(request, UUID.randomUUID(), UUID.randomUUID(), PATIENT_ID,
                UUID.randomUUID(), "EXAM", "EXAM", SOURCE_ID, EPISODE_ID, "OUTPATIENT_VISIT", SOURCE_ID, null, null,
                List.of(), new BigDecimal("150000.00"), BigDecimal.ZERO, new BigDecimal("150000.00"), "VND",
                "PENDING", "OPEN", null, Instant.parse("2026-10-16T08:00:00Z"));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void bySource_exactQuery_returnsDiscoveryDTO() throws Exception {
        when(discovery.discover(any())).thenReturn(sampleDTO());
        http.perform(validRequest().header("X-Correlation-Id", "web-trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.purpose").value("EXAM"))
                .andExpect(jsonPath("$.data.sourceId").value(SOURCE_ID.toString()))
                .andExpect(header().string("X-Correlation-Id", "web-trace"));
    }

    @Test @WithMockUser(roles = "CASHIER")
    void bySource_cashierRole_isAllowed() throws Exception {
        when(discovery.discover(any())).thenReturn(sampleDTO());
        http.perform(validRequest()).andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "PATIENT")
    void bySource_patientRole_isForbidden() throws Exception {
        http.perform(validRequest()).andExpect(status().isForbidden());
        verifyNoInteractions(discovery);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void bySource_missingKey_returns400WithoutCallingUseCase() throws Exception {
        http.perform(get("/api/v1/billing/payment-requests/by-source")
                        .param("purpose", "EXAM").param("sourceId", SOURCE_ID.toString())
                        .param("patientId", PATIENT_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(discovery);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void bySource_repeatedKey_returns400() throws Exception {
        http.perform(get("/api/v1/billing/payment-requests/by-source")
                        .param("purpose", "EXAM", "LAB_TEST").param("sourceId", SOURCE_ID.toString())
                        .param("patientId", PATIENT_ID.toString()).param("careEpisodeId", EPISODE_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(discovery);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void bySource_unknownKey_returns400() throws Exception {
        http.perform(validRequest().param("accountId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(discovery);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void bySource_malformedUuid_returns400() throws Exception {
        http.perform(get("/api/v1/billing/payment-requests/by-source")
                        .param("purpose", "EXAM").param("sourceId", "not-a-uuid")
                        .param("patientId", PATIENT_ID.toString()).param("careEpisodeId", EPISODE_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(discovery);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void bySource_unknownPurposeEnum_returns400() throws Exception {
        http.perform(get("/api/v1/billing/payment-requests/by-source")
                        .param("purpose", "ADMISSION_DEPOSIT").param("sourceId", SOURCE_ID.toString())
                        .param("patientId", PATIENT_ID.toString()).param("careEpisodeId", EPISODE_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(discovery);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void bySource_notFound_returns404() throws Exception {
        when(discovery.discover(any())).thenThrow(new LedgerPaymentRequestNotFoundException("no source"));
        http.perform(validRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BILLING_PAYMENT_REQUEST_NOT_FOUND"));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void bySource_notFound_carriesCorrelationId() throws Exception {
        when(discovery.discover(any())).thenThrow(new LedgerPaymentRequestNotFoundException("patient mismatch"));
        http.perform(validRequest().header("X-Correlation-Id", "web-trace"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").value("web-trace"))
                .andExpect(header().string("X-Correlation-Id", "web-trace"));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void bySource_notFound_generatesCorrelationIdWhenMissing() throws Exception {
        when(discovery.discover(any())).thenThrow(new LedgerPaymentRequestNotFoundException("patient mismatch"));
        http.perform(validRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void bySource_brokenMapping_returns503WithoutLeakingDetail() throws Exception {
        when(discovery.discover(any())).thenThrow(new DataIntegrityViolationException("private row detail"));
        http.perform(validRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("BILLING_LEDGER_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private row detail"))));
    }
}
