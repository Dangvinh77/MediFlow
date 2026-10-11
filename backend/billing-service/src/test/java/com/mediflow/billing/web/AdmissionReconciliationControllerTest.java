package com.mediflow.billing.web;

import java.time.Instant;
import java.util.UUID;
import com.mediflow.billing.application.port.in.ReconcileAdmissionUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(value=AdmissionReconciliationController.class, properties={"mediflow.billing.ledger.enabled=true",
        "mediflow.billing.settlement-preview.enabled=true","mediflow.billing.reconciliation.enabled=true",
        "mediflow.jwt.secret=test-only-reconciliation-secret-with-32-bytes"})
@org.springframework.context.annotation.Import(com.mediflow.billing.infrastructure.config.SecurityConfig.class)
class AdmissionReconciliationControllerTest {
    private static final String SECRET="test-only-reconciliation-secret-with-32-bytes";
    @Autowired MockMvc mvc; @MockBean ReconcileAdmissionUseCase service;
    private final UUID account=UUID.randomUUID(),actor=UUID.randomUUID();
    private String url() { return "/api/v1/billing/accounts/"+account+"/settlement-reconciliations"; }
    @ParameterizedTest @ValueSource(strings={"ADMIN","CASHIER"})
    void reconcile_allowedRoles_passesSignedActorNotClientIdentity(String role) throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles(role)).header("Idempotency-Key","key-1"))
                .andExpect(status().isCreated());
        verify(service).reconcile(new com.mediflow.billing.application.dto.command.ReconcileAdmissionCommand(account,actor,"key-1"));
    }
    @ParameterizedTest @ValueSource(strings={"DOCTOR","NURSE","PHARMACIST","MANAGER","LAB_TECH","PATIENT","SYSTEM"})
    void reconcile_otherRoles_deniedBeforeWriting(String role) throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles(role)).header("Idempotency-Key","key"))
                .andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void reconcile_anonymous_rejected() throws Exception {
        mvc.perform(post(url()).header("Idempotency-Key","key")).andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @Test void reconcile_missingKey_badRequestWithoutWriting() throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles("CASHIER"))).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void reconcile_invalidKey_rejectsWithoutWriting() throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles("CASHIER")).header("Idempotency-Key","space key"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("BILLING_RECONCILIATION_INVALID_REQUEST"));
        verifyNoInteractions(service);
    }
    @Test void reconcile_serializationFailure_returnsSafeRetryable503() throws Exception {
        when(service.reconcile(any())).thenThrow(new org.springframework.dao.CannotSerializeTransactionException("private SQL and password"));
        mvc.perform(post(url()).with(user(actor.toString()).roles("CASHIER")).header("Idempotency-Key","key"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("BILLING_LEDGER_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
    }
    @ParameterizedTest @ValueSource(strings={"refresh","service","missingType","missingExpiry","expired","invalidSubject","invalidSignature"})
    void reconcile_invalidSignedToken_deniedBeforeUseCase(String scenario) throws Exception {
        var token=io.jsonwebtoken.Jwts.builder().subject(scenario.equals("invalidSubject")?"cashier":actor.toString()).claim("role","CASHIER");
        if(!scenario.equals("missingType")) token.claim("type",scenario.equals("refresh")?"refresh":scenario.equals("service")?"service":"access");
        if(!scenario.equals("missingExpiry")) token.expiration(java.util.Date.from(Instant.now().plusSeconds(scenario.equals("expired")?-60:300)));
        var key=io.jsonwebtoken.security.Keys.hmacShaKeyFor((scenario.equals("invalidSignature")?"another-test-reconciliation-secret-32-bytes":SECRET).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(post(url()).header("Authorization","Bearer "+token.signWith(key).compact()).header("Idempotency-Key","key"))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @Test void reconcile_validSignedAccessToken_reachesUseCase() throws Exception {
        var key=io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var token=io.jsonwebtoken.Jwts.builder().subject(actor.toString()).claim("role","CASHIER").claim("type","access")
                .expiration(java.util.Date.from(Instant.now().plusSeconds(300))).signWith(key).compact();
        mvc.perform(post(url()).header("Authorization","Bearer "+token).header("Idempotency-Key","key")).andExpect(status().isCreated());
        verify(service).reconcile(new com.mediflow.billing.application.dto.command.ReconcileAdmissionCommand(account,actor,"key"));
    }
}
