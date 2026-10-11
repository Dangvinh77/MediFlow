package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.command.FinalizeAdmissionCommand;
import com.mediflow.billing.application.port.in.FinalizeAdmissionUseCase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value=FinalAdmissionSettlementController.class,properties={"mediflow.billing.ledger.enabled=true",
        "mediflow.billing.settlement-preview.enabled=true","mediflow.billing.admission-charge-close.enabled=true","mediflow.billing.final-settlement.enabled=true",
        "mediflow.jwt.secret=test-only-final-admission-settlement-secret-with-32-bytes"})
@Import(com.mediflow.billing.infrastructure.config.SecurityConfig.class)
class FinalAdmissionSettlementControllerTest {
    private static final String SECRET="test-only-final-admission-settlement-secret-with-32-bytes";
    @Autowired MockMvc mvc;
    @MockBean FinalizeAdmissionUseCase service;
    private final UUID account=UUID.randomUUID(),actor=UUID.randomUUID();
    private String url(){return "/api/v1/billing/accounts/"+account+"/final-settlements";}
    @ParameterizedTest @ValueSource(strings={"ADMIN","CASHIER"})
    void allowedRoleUsesSignedActorNotCallerMoney(String role)throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles(role)).header("Idempotency-Key","key")).andExpect(status().isCreated());
        verify(service).finalizeAdmission(new FinalizeAdmissionCommand(account,actor,"key"));
    }
    @ParameterizedTest @ValueSource(strings={"DOCTOR","NURSE","PHARMACIST","MANAGER","LAB_TECH","PATIENT","SYSTEM"})
    void otherRoleCannotFinalize(String role)throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles(role)).header("Idempotency-Key","key")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void anonymousDenied()throws Exception {
        mvc.perform(post(url()).header("Idempotency-Key","key")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @Test void missingKeyRejected()throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles("CASHIER"))).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void invalidKeyRejected()throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles("CASHIER")).header("Idempotency-Key","bad key")).andExpect(status().isUnprocessableEntity());verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"refresh","service","missingType","missingExpiry","expired","invalidSubject","invalidSignature"})
    void invalidSignedCredentialRejectedBeforeFinalization(String scenario)throws Exception {
        var token=io.jsonwebtoken.Jwts.builder().subject(scenario.equals("invalidSubject")?"cashier":actor.toString()).claim("role","CASHIER");
        if(!scenario.equals("missingType"))token.claim("type",scenario.equals("refresh")?"refresh":scenario.equals("service")?"service":"access");
        if(!scenario.equals("missingExpiry"))token.expiration(java.util.Date.from(Instant.now().plusSeconds(scenario.equals("expired")?-60:300)));
        var key=io.jsonwebtoken.security.Keys.hmacShaKeyFor((scenario.equals("invalidSignature")?"another-final-settlement-test-secret-with-32-bytes":SECRET).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(post(url()).header("Authorization","Bearer "+token.signWith(key).compact()).header("Idempotency-Key","key"))
                .andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @Test void validSignedAccessCredentialAccepted()throws Exception {
        var key=io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var token=io.jsonwebtoken.Jwts.builder().subject(actor.toString()).claim("role","CASHIER").claim("type","access")
                .expiration(java.util.Date.from(Instant.now().plusSeconds(300))).signWith(key).compact();
        mvc.perform(post(url()).header("Authorization","Bearer "+token).header("Idempotency-Key","key")).andExpect(status().isCreated());
        verify(service).finalizeAdmission(new FinalizeAdmissionCommand(account,actor,"key"));
    }
    @Test void exhaustedSnapshotContentionIsUnavailableNotSuccess()throws Exception {
        when(service.finalizeAdmission(any())).thenThrow(new org.springframework.dao.CannotSerializeTransactionException("test contention"));
        mvc.perform(post(url()).with(user(actor.toString()).roles("CASHIER")).header("Idempotency-Key","key"))
                .andExpect(status().isServiceUnavailable());
        verify(service).finalizeAdmission(new FinalizeAdmissionCommand(account,actor,"key"));
    }
}
