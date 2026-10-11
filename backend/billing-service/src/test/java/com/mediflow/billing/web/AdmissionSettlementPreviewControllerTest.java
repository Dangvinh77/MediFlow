package com.mediflow.billing.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.mediflow.billing.application.dto.response.AdmissionSettlementPreviewDTO;
import com.mediflow.billing.application.port.in.GetAdmissionSettlementPreviewUseCase;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.SettlementOutcome;
import com.mediflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(value = AdmissionSettlementPreviewController.class, properties = {
        "mediflow.billing.ledger.enabled=true", "mediflow.billing.settlement-preview.enabled=true",
        "mediflow.jwt.secret=test-only-settlement-preview-jwt-secret-32-bytes"})
@org.springframework.context.annotation.Import(com.mediflow.billing.infrastructure.config.SecurityConfig.class)
class AdmissionSettlementPreviewControllerTest {
    private static final String SECRET = "test-only-settlement-preview-jwt-secret-32-bytes";
    @Autowired MockMvc mvc;
    @MockBean GetAdmissionSettlementPreviewUseCase preview;
    private final UUID account = UUID.randomUUID();
    private String url() { return "/api/v1/billing/accounts/" + account + "/settlement-preview"; }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "CASHIER"})
    void get_allowedRole_returnsExplicitNonFinalSnapshot(String role) throws Exception {
        BigDecimal zero = new BigDecimal("0.00"); var now = Instant.now();
        when(preview.get(account)).thenReturn(new AdmissionSettlementPreviewDTO(account, UUID.randomUUID(), UUID.randomUUID(), "VND",
                AccountStatus.CHARGE_CLOSED, now, now, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                SettlementOutcome.PAID_IN_FULL, true, false));
        mvc.perform(get(url()).with(user("cashier").roles(role))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotOnly").value(true)).andExpect(jsonPath("$.data.finalSettlement").value(false))
                .andExpect(jsonPath("$.data.patientId").doesNotExist()).andExpect(jsonPath("$.data.balance").value(0));
    }
    @ParameterizedTest @ValueSource(strings = {"MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "LAB_TECH", "PATIENT", "SYSTEM"})
    void get_otherRoles_deniedBeforeReadingMoney(String role) throws Exception {
        mvc.perform(get(url()).with(user("other").roles(role))).andExpect(status().isForbidden()); verifyNoInteractions(preview);
    }
    @Test void get_anonymous_deniedBeforeRead() throws Exception {
        mvc.perform(get(url())).andExpect(status().isUnauthorized()); verifyNoInteractions(preview);
    }
    @Test @WithMockUser(roles = "CASHIER") void get_missingAccount_notZero() throws Exception {
        when(preview.get(account)).thenThrow(new ResourceNotFoundException("BILLING_ACCOUNT_NOT_FOUND", "not found"));
        mvc.perform(get(url())).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("BILLING_ACCOUNT_NOT_FOUND"));
    }
    @Test @WithMockUser(roles = "CASHIER") void get_openCharges_notFinalizable() throws Exception {
        when(preview.get(account)).thenThrow(new BillingRuleException("BILLING_SETTLEMENT_NOT_READY", "not ready"));
        mvc.perform(get(url())).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("BILLING_SETTLEMENT_NOT_READY"));
    }
    @Test @WithMockUser(roles = "CASHIER") void get_storageFailure_503WithoutPrivateLedgerDetail() throws Exception {
        when(preview.get(account)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private database password and SQL"));
        mvc.perform(get(url()).header("X-Correlation-Id", "preview-correlation")).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Correlation-Id", "preview-correlation"))
                .andExpect(jsonPath("$.error.code").value("BILLING_LEDGER_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
    }
    @Test @WithMockUser(roles = "ADMIN") void get_invalidUuid_400BeforeRead() throws Exception {
        mvc.perform(get("/api/v1/billing/accounts/not-a-uuid/settlement-preview")).andExpect(status().isBadRequest()); verifyNoInteractions(preview);
    }
    @ParameterizedTest @ValueSource(strings = {"refresh", "service", "missingType", "missingExpiry", "expired", "nonUuidSubject", "invalidSignature"})
    void get_invalidSignedCredential_rejectedBeforeLedgerRead(String scenario) throws Exception {
        var builder = io.jsonwebtoken.Jwts.builder().subject(scenario.equals("nonUuidSubject") ? "cashier" : UUID.randomUUID().toString())
                .claim("role", "CASHIER");
        if (!scenario.equals("missingType")) builder.claim("type", scenario.equals("refresh") ? "refresh" : scenario.equals("service") ? "service" : "access");
        if (!scenario.equals("missingExpiry")) builder.expiration(java.util.Date.from(Instant.now().plusSeconds(scenario.equals("expired") ? -60 : 300)));
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor((scenario.equals("invalidSignature") ? "different-test-only-settlement-secret-32-bytes" : SECRET)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get(url()).header("Authorization", "Bearer " + builder.signWith(key).compact())).andExpect(status().isUnauthorized());
        verifyNoInteractions(preview);
    }
    @Test void get_signedAccessToken_reachesUseCaseWithoutMockAuthentication() throws Exception {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var token = io.jsonwebtoken.Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", "CASHIER").claim("type", "access")
                .expiration(java.util.Date.from(Instant.now().plusSeconds(300))).signWith(key).compact();
        when(preview.get(account)).thenThrow(new ResourceNotFoundException("BILLING_ACCOUNT_NOT_FOUND", "not found"));
        mvc.perform(get(url()).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound()); verify(preview).get(account);
    }
}
