package com.mediflow.clinical.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.mediflow.clinical.application.dto.authority.RecordReadDecision;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.service.RecordReadGuard;
import com.mediflow.clinical.infrastructure.config.SecurityConfig;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import com.mediflow.common.security.JwtClaims;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(controllers = RecordReadDecisionController.class, properties =
        "mediflow.jwt.secret=clinical-context-test-secret-at-least-32-bytes")
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class RecordReadDecisionControllerTest {
    private static final String SECRET = "clinical-context-test-secret-at-least-32-bytes";
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-000000002001");
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000002002");
    private static final UUID STAFF = UUID.fromString("00000000-0000-0000-0000-000000002003");

    @Autowired
    private MockMvc mvc;
    @MockBean
    private RecordReadGuard guard;

    @Test
    void humanTokenIsRejected() throws Exception {
        mvc.perform(request().header("Authorization", "Bearer " + token("user-1", JwtClaims.ACCESS_TOKEN_TYPE, "DOCTOR")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(guard);
    }

    @Test
    void otherServicePrincipalIsRejected() throws Exception {
        mvc.perform(request().header("Authorization", "Bearer " + token("billing-service", JwtClaims.SERVICE_TOKEN_TYPE, "SYSTEM")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(guard);
    }

    @Test
    void returnsDecisionWithoutClinicalContent() throws Exception {
        when(guard.decideForActor(any(), eq(PATIENT))).thenReturn(RecordReadDecision.allow("CARE_ASSIGNMENT"));

        var result = mvc.perform(request().header("Authorization", "Bearer " + labToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value(PATIENT.toString()))
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.basis").value("CARE_ASSIGNMENT"))
                .andExpect(jsonPath("$.data.denialCode").doesNotExist())
                .andExpect(jsonPath("$.data.observedAt").exists())
                .andReturn();

        com.fasterxml.jackson.databind.JsonNode data = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.size()).isEqualTo(5);
    }

    @Test
    void actorIdentityComesFromQueryNotFromServiceToken() throws Exception {
        when(guard.decideForActor(any(), eq(PATIENT))).thenReturn(RecordReadDecision.deny("RECORD_ACCESS_PURPOSE_REQUIRED"));

        mvc.perform(request().header("Authorization", "Bearer " + token("pharmacy-service", JwtClaims.SERVICE_TOKEN_TYPE, "SYSTEM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(false))
                .andExpect(jsonPath("$.data.denialCode").value("RECORD_ACCESS_PURPOSE_REQUIRED"));

        ArgumentCaptor<ActorIdentity> actor = ArgumentCaptor.forClass(ActorIdentity.class);
        verify(guard).decideForActor(actor.capture(), eq(PATIENT));
        assertThat(actor.getValue().accountId()).isEqualTo(ACCOUNT);
        assertThat(actor.getValue().staffId()).isEqualTo(STAFF);
        assertThat(actor.getValue().role()).isEqualTo("DOCTOR");
    }

    private static MockHttpServletRequestBuilder request() {
        return get("/api/v2/clinical/patients/{patientId}/read-decision", PATIENT)
                .param("actorAccountId", ACCOUNT.toString())
                .param("actorStaffId", STAFF.toString())
                .param("actorRole", "DOCTOR");
    }

    private static String labToken() {
        return token("lab-service", JwtClaims.SERVICE_TOKEN_TYPE, "SYSTEM");
    }

    private static String token(String subject, String type, String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(subject)
                .claim(JwtClaims.TYPE, type)
                .claim(JwtClaims.ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(30)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
