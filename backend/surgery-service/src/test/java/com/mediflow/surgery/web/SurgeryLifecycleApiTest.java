package com.mediflow.surgery.web;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.port.in.*;
import com.mediflow.surgery.infrastructure.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=SurgeryLifecycleController.class,properties={"mediflow.features.surgery.enabled=true",
        "mediflow.surgery.lifecycle.api.enabled=true","mediflow.jwt.secret=lifecycle-api-test-secret-with-at-least-32-bytes"})
@Import(SecurityConfig.class)
class SurgeryLifecycleApiTest {
    private static final String SECRET="lifecycle-api-test-secret-with-at-least-32-bytes";
    private static final UUID CASE=UUID.randomUUID();
    private static final UUID ACCOUNT=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BODY="{\"expectedCaseRevision\":3,\"expectedScheduleRevision\":1}";
    private static final String COMPLETE="""
            {"expectedCaseRevision":4,"expectedScheduleRevision":1,"procedureCode":"TEST-PROCEDURE",
             "methodCode":"TEST-METHOD","outcomeCode":"TEST-OUTCOME",
             "actualStartAt":"2026-10-07T08:00:00Z","actualEndAt":"2026-10-07T09:00:00Z",
             "performedItems":[{"performedItemId":"00000000-0000-0000-0000-000000000003",
             "itemCode":"TEST-ITEM","priceCode":"TEST-PRICE","quantity":0.5}]}
            """;
    @Autowired MockMvc mvc;
    @MockBean EvaluateSurgeryReadinessUseCase readiness;
    @MockBean FinalizeSurgeryScheduleUseCase scheduling;
    @MockBean StartSurgeryUseCase starting;
    @MockBean CompleteSurgeryUseCase completion;

    @ParameterizedTest @CsvSource({"ADMIN,readiness/evaluate","DOCTOR,readiness/evaluate",
            "ADMIN,schedule/finalize","MANAGER,schedule/finalize","DOCTOR,schedule/finalize",
            "ADMIN,start","DOCTOR,start","ADMIN,complete","DOCTOR,complete"})
    void permittedRoleDelegatesTrustedIdentity(String role,String suffix) throws Exception {
        var response=outcome("APPLIED",List.of());
        when(readiness.evaluate(any())).thenReturn(response); when(scheduling.finalizeSchedule(any())).thenReturn(response);
        when(starting.start(any())).thenReturn(response); when(completion.complete(any())).thenReturn(response);
        String correlation=UUID.randomUUID().toString();
        mvc.perform(post(path(suffix)).header("Authorization","Bearer "+token(role)).header("Idempotency-Key","lifecycle-api")
                .header("X-Correlation-Id",correlation).contentType(MediaType.APPLICATION_JSON).content(body(suffix)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correlationId").value(correlation));
        switch(suffix) {
            case "readiness/evaluate" -> verify(readiness).evaluate(argThat(c -> c.actor().accountId().equals(ACCOUNT) && c.correlationId().equals(correlation)));
            case "schedule/finalize" -> verify(scheduling).finalizeSchedule(argThat(c -> c.actor().accountId().equals(ACCOUNT)));
            case "start" -> verify(starting).start(argThat(c -> c.actor().accountId().equals(ACCOUNT)));
            default -> verify(completion).complete(argThat(c -> c.identity().actor().accountId().equals(ACCOUNT)
                    && c.performedItems().getFirst().quantity().compareTo(new java.math.BigDecimal("0.5"))==0));
        }
        verifyNoMoreInteractions(readiness,scheduling,starting,completion);
    }
    @ParameterizedTest @ValueSource(strings={"NURSE","PATIENT","PHARMACIST","CASHIER","LAB_TECH"})
    void forbiddenRolesNeverReachUseCases(String role) throws Exception {
        for(String suffix:suffixes()) request(suffix,role,body(suffix)).andExpect(status().isForbidden());
        verifyNoInteractions(readiness,scheduling,starting,completion);
    }
    @Test void managerCannotAssertClinicalTransitions() throws Exception {
        for(String suffix:new String[]{"readiness/evaluate","start","complete"}) request(suffix,"MANAGER",body(suffix)).andExpect(status().isForbidden());
        verifyNoInteractions(readiness,scheduling,starting,completion);
    }
    @ParameterizedTest @ValueSource(strings={"financialOverride","financialClearanceValid","emergency","ready","actorId","role","status"})
    void injectedAuthorityIsRejectedEvenForAdmin(String field) throws Exception {
        for(String suffix:suffixes()) request(suffix,"ADMIN",body(suffix).replaceFirst("\\{","{\""+field+"\":true,"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(readiness,scheduling,starting,completion);
    }
    @Test void adminReceivesFinancialDenialWithoutBypass() throws Exception {
        var denial=outcome("NOT_READY",List.of("FINANCIAL_CLEARANCE_INVALID"));
        when(readiness.evaluate(any())).thenReturn(denial); when(starting.start(any())).thenReturn(denial);
        for(String suffix:new String[]{"readiness/evaluate","start"}) request(suffix,"ADMIN",BODY).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("NOT_READY"))
                .andExpect(jsonPath("$.data.blockingReasons[0]").value("FINANCIAL_CLEARANCE_INVALID"));
        verify(readiness).evaluate(any()); verify(starting).start(any()); verifyNoInteractions(scheduling,completion);
    }
    @Test void missingAuthenticationKeyOrRevisionHasNoEffect() throws Exception {
        for(String suffix:suffixes()) {
            mvc.perform(post(path(suffix)).header("Idempotency-Key","deny").contentType(MediaType.APPLICATION_JSON).content(body(suffix)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path(suffix)).header("Authorization","Bearer "+token("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(suffix)))
                    .andExpect(status().isBadRequest());
            for(String invalid:new String[]{"{}",body(suffix).replace("\"expectedScheduleRevision\":1","\"expectedScheduleRevision\":0")})
                request(suffix,"ADMIN",invalid).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(readiness,scheduling,starting,completion);
    }
    @Test void completionRejectsAmountsNonpositiveQuantityAndReversedTime() throws Exception {
        for(String invalid:new String[]{COMPLETE.replace("\"quantity\":0.5","\"quantity\":0"),
                COMPLETE.replace("\"quantity\":0.5","\"quantity\":0.5,\"amount\":1000"),
                COMPLETE.replace("2026-10-07T09:00:00Z","2026-10-07T07:00:00Z")})
            request("complete","DOCTOR",invalid).andExpect(status().isBadRequest());
        verifyNoInteractions(completion);
    }
    @Test void busyAndUpstreamFailureAreTypedWithoutPrivateDetail() throws Exception {
        when(starting.start(any())).thenThrow(new com.mediflow.surgery.application.exception.SurgeryCommandBusyException());
        request("start","DOCTOR",BODY).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SURGERY_COMMAND_BUSY"));
        doThrow(new com.mediflow.surgery.application.exception.UpstreamUnavailableException("private")).when(starting).start(any());
        request("start","DOCTOR",BODY).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("SURGERY_UPSTREAM_UNAVAILABLE"));
    }
    @ParameterizedTest
    @ValueSource(strings={"0.00001","1.00001","999999999999999.99999","1000000000000000","1E15","1E-20"})
    void completion_quantityWouldRoundOrOverflow_rejectedBeforeUseCase(String quantity) throws Exception {
        request("complete","DOCTOR",COMPLETE.replace("\"quantity\":0.5","\"quantity\":"+quantity))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(completion);
    }
    @ParameterizedTest
    @ValueSource(strings={"0.0001","999999999999999.9999","1E14"})
    void completion_exactStorageBoundary_preservesRequestedQuantity(String quantity) throws Exception {
        when(completion.complete(any())).thenReturn(outcome("COMPLETED",List.of()));
        request("complete","DOCTOR",COMPLETE.replace("\"quantity\":0.5","\"quantity\":"+quantity))
                .andExpect(status().isOk());
        verify(completion).complete(argThat(command -> command.performedItems().getFirst().quantity()
                .compareTo(new java.math.BigDecimal(quantity))==0));
    }
    private org.springframework.test.web.servlet.ResultActions request(String suffix,String role,String content) throws Exception {
        return mvc.perform(post(path(suffix)).header("Authorization","Bearer "+token(role)).header("Idempotency-Key","api-test")
                .contentType(MediaType.APPLICATION_JSON).content(content));
    }
    private static String[] suffixes() { return new String[]{"readiness/evaluate","schedule/finalize","start","complete"}; }
    private static String body(String suffix) { return suffix.equals("complete") ? COMPLETE : BODY; }
    private static String path(String suffix) { return "/api/v1/surgery/cases/"+CASE+"/"+suffix; }
    private static SurgeryCommandOutcome outcome(String state,List<String> reasons) {
        return new SurgeryCommandOutcome("LOCAL_TEST",CASE,4,UUID.randomUUID(),1,state,Instant.now(),false,reasons);
    }
    private static String token(String role) {
        return Jwts.builder().subject(ACCOUNT.toString()).claim("staffId","00000000-0000-0000-0000-000000000002")
                .claim("type","access").claim("role",role).expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
