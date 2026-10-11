package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.command.AllocateAdmissionDepositCommand;
import com.mediflow.billing.application.port.in.AllocateAdmissionDepositUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value=AdmissionDepositAllocationController.class,properties={"mediflow.billing.ledger.enabled=true",
        "mediflow.billing.settlement-preview.enabled=true","mediflow.billing.deposit-allocation.enabled=true",
        "mediflow.jwt.secret=test-only-deposit-allocation-secret-with-32-bytes"})
@Import(com.mediflow.billing.infrastructure.config.SecurityConfig.class)
class AdmissionDepositAllocationControllerTest {
    private static final String SECRET="test-only-deposit-allocation-secret-with-32-bytes";
    @Autowired MockMvc mvc;
    @MockBean AllocateAdmissionDepositUseCase service;
    private final UUID account=UUID.randomUUID(),original=UUID.randomUUID(),charge=UUID.randomUUID(),actor=UUID.randomUUID();
    private String url(){return "/api/v1/billing/accounts/"+account+"/deposit-allocations";}
    private String body(){return "{\"originalTransactionId\":\""+original+"\",\"allocations\":[{\"chargeId\":\""+charge+"\",\"amount\":80.00}]}";}
    @ParameterizedTest @ValueSource(strings={"ADMIN","CASHIER"})
    void allowedRolesUseAuthenticatedActorAndExactSelectedAmounts(String role) throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles(role)).header("Idempotency-Key","key")
                .contentType("application/json").content(body())).andExpect(status().isCreated());
        verify(service).allocate(new AllocateAdmissionDepositCommand(account,original,actor,"key",Map.of(charge,new BigDecimal("80.00"))));
    }
    @ParameterizedTest @ValueSource(strings={"DOCTOR","NURSE","PHARMACIST","MANAGER","LAB_TECH","PATIENT","SYSTEM"})
    void otherRolesDeniedBeforeAllocation(String role) throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles(role)).header("Idempotency-Key","key")
                .contentType("application/json").content(body())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void anonymousDeniedBeforeAllocation() throws Exception {
        mvc.perform(post(url()).header("Idempotency-Key","key").contentType("application/json").content(body()))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @Test void missingKeyRejectedBeforeAllocation() throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles("CASHIER")).contentType("application/json").content(body()))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void invalidKeyRejectedBeforeAllocation() throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles("CASHIER")).header("Idempotency-Key","bad key")
                .contentType("application/json").content(body())).andExpect(status().isUnprocessableEntity()); verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","100000000000000000"})
    void invalidAmountDoesNotReachApplication(String amount) throws Exception {
        mvc.perform(post(url()).with(user(actor.toString()).roles("CASHIER")).header("Idempotency-Key","key")
                .contentType("application/json").content(body().replace("80.00",amount))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void duplicateChargeDoesNotSilentlyOverwriteIntent() throws Exception {
        String line="{\"chargeId\":\""+charge+"\",\"amount\":40.00}";
        String duplicated="{\"originalTransactionId\":\""+original+"\",\"allocations\":["+line+","+line+"]}";
        mvc.perform(post(url()).with(user(actor.toString()).roles("CASHIER")).header("Idempotency-Key","key")
                .contentType("application/json").content(duplicated)).andExpect(status().isUnprocessableEntity()); verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"refresh","service","missingType","missingExpiry","expired","invalidSubject","invalidSignature"})
    void invalidSignedCredentialDeniedBeforeUseCase(String scenario) throws Exception {
        var token=io.jsonwebtoken.Jwts.builder().subject(scenario.equals("invalidSubject")?"cashier":actor.toString()).claim("role","CASHIER");
        if(!scenario.equals("missingType"))token.claim("type",scenario.equals("refresh")?"refresh":scenario.equals("service")?"service":"access");
        if(!scenario.equals("missingExpiry"))token.expiration(java.util.Date.from(Instant.now().plusSeconds(scenario.equals("expired")?-60:300)));
        var key=io.jsonwebtoken.security.Keys.hmacShaKeyFor((scenario.equals("invalidSignature")?"another-test-deposit-secret-with-32-bytes":SECRET).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(post(url()).header("Authorization","Bearer "+token.signWith(key).compact()).header("Idempotency-Key","key")
                .contentType("application/json").content(body())).andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @Test void validSignedAccessCredentialReachesUseCase() throws Exception {
        var key=io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var token=io.jsonwebtoken.Jwts.builder().subject(actor.toString()).claim("role","CASHIER").claim("type","access")
                .expiration(java.util.Date.from(Instant.now().plusSeconds(300))).signWith(key).compact();
        mvc.perform(post(url()).header("Authorization","Bearer "+token).header("Idempotency-Key","key")
                .contentType("application/json").content(body())).andExpect(status().isCreated());
        verify(service).allocate(new AllocateAdmissionDepositCommand(account,original,actor,"key",Map.of(charge,new BigDecimal("80.00"))));
    }
}
