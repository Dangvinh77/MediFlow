package com.mediflow.report.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest;
import com.mediflow.report.application.exception.OutpatientCoverageConflictException;
import com.mediflow.report.application.port.in.AcceptOutpatientCoverageUseCase;
import com.mediflow.report.application.port.out.OutpatientCoverageEvidencePort.Snapshot;
import com.mediflow.report.infrastructure.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OutpatientCoverageAcceptanceController.class)
@ActiveProfiles("outpatient-mvp")
@Import({SecurityConfig.class,GlobalExceptionHandler.class})
@TestPropertySource(properties={"mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true","mediflow.report.outpatient-read.enabled=true",
        "mediflow.report.outpatient-coverage-acceptance.enabled=true"})
class OutpatientCoverageAcceptanceControllerTest {
    private static final String PREFIX="/api/v1/reports/outpatient/coverage";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean AcceptOutpatientCoverageUseCase approvals;
    private final UUID actor=UUID.randomUUID();
    private String token(String role,String subject,String type,Instant expires) {
        var builder=Jwts.builder().subject(subject).claim(JwtClaims.ROLE,role).claim(JwtClaims.TYPE,type);
        if(expires!=null) builder.expiration(Date.from(expires));
        return "Bearer "+builder.signWith(Keys.hmacShaKeyFor("test-secret-must-have-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
    private String access(String role) { return token(role,actor.toString(),"access",Instant.now().plusSeconds(300)); }
    private AcceptOutpatientCoverageRequest request() {
        return new AcceptOutpatientCoverageRequest(UUID.randomUUID(),LocalDate.of(2026,10,1),LocalDate.of(2026,10,1),
                null,"Asia/Bangkok","VND",Map.of(),Instant.parse("2026-10-02T00:00:00Z"),
                Instant.parse("2026-10-02T00:00:00Z"),"REVIEW","a".repeat(64));
    }
    @Test void administratorActorComesFromSignedSubjectAndResponsesAreNotCached() throws Exception {
        var body=request();
        mvc.perform(post(PREFIX+"/acceptances").header("Authorization",access("ADMIN"))
                        .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(approvals).accept(body,actor);
        when(approvals.preview(body.fromDate(),body.toDate(),null)).thenReturn(new Snapshot("a".repeat(64),false,Map.of()));
        mvc.perform(get(PREFIX+"/preview").header("Authorization",access("ADMIN"))
                        .param("fromDate","2026-10-01").param("toDate","2026-10-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fingerprint").value("a".repeat(64)))
                .andExpect(header().string("Cache-Control","no-store"));
    }
    @ParameterizedTest @ValueSource(strings={"MANAGER","DOCTOR","NURSE","PHARMACIST","CASHIER","PATIENT","SYSTEM"})
    void readRolesCannotApproveOrInspectSourceWitness(String role) throws Exception {
        mvc.perform(post(PREFIX+"/acceptances").header("Authorization",access(role))
                .contentType("application/json").content(mapper.writeValueAsString(request()))).andExpect(status().isForbidden());
        mvc.perform(get(PREFIX+"/preview").header("Authorization",access(role))
                .param("fromDate","2026-10-01").param("toDate","2026-10-01")).andExpect(status().isForbidden());
        verifyNoInteractions(approvals);
    }
    @Test void missingExpiryExpiredAndRefreshTokensNeverReachApprovals() throws Exception {
        for(String credential:List.of(token("ADMIN",actor.toString(),"access",null),
                token("ADMIN",actor.toString(),"access",Instant.now().minusSeconds(60)),
                token("ADMIN",actor.toString(),"refresh",Instant.now().plusSeconds(300)),
                token("ADMIN",actor.toString(),"service",Instant.now().plusSeconds(300)))) {
            mvc.perform(post(PREFIX+"/acceptances").header("Authorization",credential)
                    .contentType("application/json").content(mapper.writeValueAsString(request()))).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(approvals);
    }
    @Test void nonUuidSignedAdministratorCannotInventActor() throws Exception {
        mvc.perform(post(PREFIX+"/acceptances").header("Authorization",token("ADMIN","operator-name","access",Instant.now().plusSeconds(300)))
                .contentType("application/json").content(mapper.writeValueAsString(request()))).andExpect(status().isForbidden());
        verifyNoInteractions(approvals);
    }
    @Test void changedEvidenceIsConflictNotAcceptedZero() throws Exception {
        when(approvals.accept(any(),any())).thenThrow(new OutpatientCoverageConflictException());
        mvc.perform(post(PREFIX+"/acceptances").header("Authorization",access("ADMIN"))
                .contentType("application/json").content(mapper.writeValueAsString(request())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REPORT_COVERAGE_CONFLICT"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
    @Test void omittedSourceCountCannotBecomeAnImplicitZeroAttestation() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode body=mapper.valueToTree(request());
        var proof=body.putObject("sources").putObject("LAB");
        proof.put("reference","SYNTHETIC_EXPORT").put("exportSha256","b".repeat(64))
                .put("localFingerprint","a".repeat(64));
        mvc.perform(post(PREFIX+"/acceptances").header("Authorization",access("ADMIN"))
                .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        proof.putNull("rowCount");
        mvc.perform(post(PREFIX+"/acceptances").header("Authorization",access("ADMIN"))
                .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(approvals);
    }
}
