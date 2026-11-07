package com.mediflow.billing.web;

import static org.hamcrest.Matchers.is;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.billing.application.dto.response.PaymentHistoryItem;
import com.mediflow.billing.application.port.in.FindPaymentHistoryUseCase;
import com.mediflow.billing.infrastructure.config.SecurityConfig;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(PaymentHistoryController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"mediflow.billing.ledger.enabled=true",
        "mediflow.billing.payment-request-discovery.enabled=true",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"})
class PaymentHistoryControllerTest {
    private static final String PATH = "/api/v1/billing/patient/payments";
    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";

    @Autowired MockMvc http;
    @MockBean FindPaymentHistoryUseCase history;

    private String access(String role) {
        return Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", role).claim("type", "access")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CASHIER"})
    void returnsLedgerMovementsWithNoStore(String role) throws Exception {
        UUID patient = UUID.randomUUID();
        PaymentHistoryItem paid = new PaymentHistoryItem(UUID.randomUUID(), UUID.randomUUID(), patient,
                UUID.randomUUID(), "PAYMENT", "LAB_TEST", new BigDecimal("80000.00"), "VND", "CASH",
                Instant.parse("2026-11-07T03:00:00Z"));
        when(history.find(patient, PageQuery.of(0, 20))).thenReturn(PageResult.of(List.of(paid), 1L, 0, 20));
        http.perform(get(PATH).param("patientId", patient.toString()).param("page", "0").param("size", "20")
                        .header("Authorization", "Bearer " + access(role)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.content[0].purpose", is("LAB_TEST")))
                .andExpect(jsonPath("$.data.content[0].transactionType", is("PAYMENT")));
    }

    @Test
    void omittedPatientListsEveryPatient() throws Exception {
        when(history.find(null, PageQuery.of(null, null))).thenReturn(PageResult.empty(PageQuery.of(null, null)));
        http.perform(get(PATH).header("Authorization", "Bearer " + access("CASHIER"))).andExpect(status().isOk());
        verify(history).find(null, PageQuery.of(null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "NURSE", "LAB_TECH", "PHARMACIST", "PATIENT"})
    void otherRolesAreDenied(String role) throws Exception {
        http.perform(get(PATH).header("Authorization", "Bearer " + access(role))).andExpect(status().isForbidden());
        verifyNoInteractions(history);
    }

    @Test
    void anonymousIsRejected() throws Exception {
        http.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(history);
    }
}
