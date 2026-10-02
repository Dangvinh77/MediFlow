package com.mediflow.surgery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.port.in.BeginPreopUseCase;
import com.mediflow.surgery.application.port.in.CancelSurgeryUseCase;
import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "mediflow.features.surgery.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SurgeryServiceSmokeTest {

    private static final String SECRET = "surgery-test-secret-at-least-32-bytes";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CancelSurgeryUseCase cancellation;

    @MockBean
    private BeginPreopUseCase beginPreop;

    @Test
    void health_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unknownSurgeryApi_withoutToken_isUnauthorizedWithCorrelation() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/surgery/cases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andReturn();

        assertThat(UUID.fromString(result.getResponse().getHeader(JwtClaims.HEADER_CORRELATION_ID)))
                .isNotNull();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("correlationId").asText())
                .isEqualTo(result.getResponse().getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }

    @Test
    void noBusinessPlaceholder_returnsNotFoundEvenForAuthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/v1/surgery/cases")
                        .header("Authorization", "Bearer " + validAccessToken("DOCTOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_doctorWithAccessTokenCallsUseCaseAndReturnsApiEnvelope() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        when(cancellation.cancel(any())).thenReturn(new SurgeryCommandOutcome("CANCEL_SURGERY",
                caseId, 4, scheduleId, 2, "CANCELLED", Instant.parse("2026-09-29T01:00:00Z"), false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surgery/cases/{surgeryCaseId}/cancel", caseId)
                        .header("Authorization", "Bearer " + validAccessToken("DOCTOR"))
                        .header("Idempotency-Key", "cancel-http-1")
                        .header(JwtClaims.HEADER_CORRELATION_ID, correlationId.toString())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedCaseRevision":3,"reason":"Patient request"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.surgeryCaseId").value(caseId.toString()))
                .andExpect(jsonPath("$.data.caseRevision").value(4))
                .andExpect(jsonPath("$.data.scheduleId").value(scheduleId.toString()))
                .andExpect(jsonPath("$.data.scheduleRevision").value(2))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()));

        org.mockito.ArgumentCaptor<CancelSurgeryUseCase.Command> command =
                org.mockito.ArgumentCaptor.forClass(CancelSurgeryUseCase.Command.class);
        verify(cancellation).cancel(command.capture());
        assertThat(command.getValue().actor().accountId())
                .isEqualTo(UUID.fromString("9d2c0477-91e7-4e92-82ae-e18c82f11375"));
        assertThat(command.getValue().actor().verifiedStaffId())
                .isEqualTo(UUID.fromString("047fa970-69d7-4c8c-8433-7d73c61b0001"));
    }

    @Test
    void cancel_withoutAuthenticationIsUnauthorized() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surgery/cases/{surgeryCaseId}/cancel", UUID.randomUUID())
                        .header("Idempotency-Key", "cancel-http-unauthenticated")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"expectedCaseRevision\":0,\"reason\":\"Patient request\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verify(cancellation, never()).cancel(any());
    }

    @Test
    void cancel_nurseRoleIsForbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surgery/cases/{surgeryCaseId}/cancel", UUID.randomUUID())
                        .header("Authorization", "Bearer " + validAccessToken("NURSE"))
                        .header("Idempotency-Key", "cancel-http-nurse")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"expectedCaseRevision\":0,\"reason\":\"Patient request\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verify(cancellation, never()).cancel(any());
    }

    @Test
    void cancel_invalidRevisionReturnsValidationEnvelope() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surgery/cases/{surgeryCaseId}/cancel", UUID.randomUUID())
                        .header("Authorization", "Bearer " + validAccessToken("DOCTOR"))
                        .header("Idempotency-Key", "cancel-http-invalid")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"expectedCaseRevision\":-1,\"reason\":\"Patient request\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("expectedCaseRevision"));
        verify(cancellation, never()).cancel(any());
    }

    @Test
    void cancel_missingIdempotencyHeaderReturnsEnvelopeShapedBadRequest() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surgery/cases/{surgeryCaseId}/cancel", UUID.randomUUID())
                        .header("Authorization", "Bearer " + validAccessToken("DOCTOR"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"expectedCaseRevision\":0,\"reason\":\"Patient request\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verify(cancellation, never()).cancel(any());
    }

    @Test
    void cancel_idempotencyKeyOverMaximumLengthReturnsValidationError() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surgery/cases/{surgeryCaseId}/cancel", UUID.randomUUID())
                        .header("Authorization", "Bearer " + validAccessToken("DOCTOR"))
                        .header("Idempotency-Key", "x".repeat(161))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"expectedCaseRevision\":0,\"reason\":\"Patient request\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(cancellation, never()).cancel(any());
    }

    @Test
    void beginPreop_doctorWithAccessTokenCallsUseCaseAndReturnsApiEnvelope() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-09-29T01:00:00Z");
        when(beginPreop.begin(any())).thenReturn(new SurgeryCommandOutcome("BEGIN_PREOP",
                caseId, 2, null, 0, "PREOP_IN_PROGRESS", startedAt, false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surgery/cases/{surgeryCaseId}/preop", caseId)
                        .header("Authorization", "Bearer " + validAccessToken("DOCTOR"))
                        .header("Idempotency-Key", "begin-preop-http-1")
                        .header(JwtClaims.HEADER_CORRELATION_ID, correlationId.toString())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"expectedCaseRevision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.surgeryCaseId").value(caseId.toString()))
                .andExpect(jsonPath("$.data.caseRevision").value(2))
                .andExpect(jsonPath("$.data.status").value("PREOP_IN_PROGRESS"))
                .andExpect(jsonPath("$.data.startedAt").value(startedAt.toString()))
                .andExpect(jsonPath("$.data.replayed").value(false))
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()));

        org.mockito.ArgumentCaptor<BeginPreopUseCase.Command> command =
                org.mockito.ArgumentCaptor.forClass(BeginPreopUseCase.Command.class);
        verify(beginPreop).begin(command.capture());
        assertThat(command.getValue().expectedCaseRevision()).isEqualTo(1);
        assertThat(command.getValue().idempotencyKey()).isEqualTo("begin-preop-http-1");
        assertThat(command.getValue().actor().accountId())
                .isEqualTo(UUID.fromString("9d2c0477-91e7-4e92-82ae-e18c82f11375"));
        assertThat(command.getValue().actor().verifiedStaffId())
                .isEqualTo(UUID.fromString("047fa970-69d7-4c8c-8433-7d73c61b0001"));
    }

    @Test
    void beginPreop_withoutAuthenticationIsUnauthorized() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surgery/cases/{surgeryCaseId}/preop", UUID.randomUUID())
                        .header("Idempotency-Key", "begin-preop-unauthenticated")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"expectedCaseRevision\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verify(beginPreop, never()).begin(any());
    }

    @Test
    void beginPreop_nurseRoleIsForbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surgery/cases/{surgeryCaseId}/preop", UUID.randomUUID())
                        .header("Authorization", "Bearer " + validAccessToken("NURSE"))
                        .header("Idempotency-Key", "begin-preop-nurse")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"expectedCaseRevision\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verify(beginPreop, never()).begin(any());
    }

    @Test
    void beginPreop_invalidRevisionIsValidationErrorBeforeUseCase() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surgery/cases/{surgeryCaseId}/preop", UUID.randomUUID())
                        .header("Authorization", "Bearer " + validAccessToken("DOCTOR"))
                        .header("Idempotency-Key", "begin-preop-invalid-revision")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"expectedCaseRevision\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(beginPreop, never()).begin(any());
    }

    @Test
    void beginPreop_missingCaseReturnsNotFoundEnvelope() throws Exception {
        when(beginPreop.begin(any())).thenThrow(
                new com.mediflow.surgery.domain.exception.SurgeryCaseNotFoundException(UUID.randomUUID()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/surgery/cases/{surgeryCaseId}/preop", UUID.randomUUID())
                        .header("Authorization", "Bearer " + validAccessToken("DOCTOR"))
                        .header("Idempotency-Key", "begin-preop-missing-case")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"expectedCaseRevision\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SURGERY_CASE_NOT_FOUND"));
    }

    private String validAccessToken(String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("9d2c0477-91e7-4e92-82ae-e18c82f11375")
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE)
                .claim(JwtClaims.STAFF_ID, "047fa970-69d7-4c8c-8433-7d73c61b0001")
                .issuedAt(Date.from(now.minusSeconds(5)))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(SIGNING_KEY)
                .compact();
    }
}
