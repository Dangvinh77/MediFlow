package com.mediflow.clinical.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.clinical.application.dto.response.ClinicalLabOrderContextDTO;
import com.mediflow.clinical.application.port.in.GetClinicalLabOrderContextUseCase;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.infrastructure.config.SecurityConfig;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ClinicalLabOrderContextController.class, properties =
        "mediflow.jwt.secret=clinical-context-test-secret-at-least-32-bytes")
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class ClinicalLabOrderContextControllerTest {
    private static final String SECRET = "clinical-context-test-secret-at-least-32-bytes";
    private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID STAFF = UUID.fromString("00000000-0000-0000-0000-000000001004");
    private static final String CORRELATION = "00000000-0000-0000-0000-000000001099";
    @Autowired MockMvc mvc;
    @MockBean GetClinicalLabOrderContextUseCase contexts;

    @BeforeEach
    void resetContext() {
        var observedAt = Instant.now();
        when(contexts.get(ORDER)).thenReturn(new ClinicalLabOrderContextDTO(true, ORDER,
                UUID.fromString("00000000-0000-0000-0000-000000001002"),
                UUID.fromString("00000000-0000-0000-0000-000000001003"), STAFF,
                UUID.fromString("00000000-0000-0000-0000-000000001005"), CareEpisodeType.OUTPATIENT_VISIT,
                UUID.fromString("00000000-0000-0000-0000-000000001006"), "CBC",
                LocalDate.of(2026, 9, 21), "LAB-CBC", observedAt.minusSeconds(1), observedAt));
    }

    @Test
    void get_labServiceCredentialReturnsExactPersistedAuthority() throws Exception {
        mvc.perform(get(path()).header("Authorization", "Bearer " + token("lab-service", STAFF, "DOCTOR"))
                        .header("X-Correlation-Id", CORRELATION))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", CORRELATION))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceOrderId").value(ORDER.toString()))
                .andExpect(jsonPath("$.data.requestingDoctorId").value(STAFF.toString()))
                .andExpect(jsonPath("$.data.priceCode").value("LAB-CBC"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION));
    }

    @Test
    void get_mismatchedSignedActorIsForbiddenAndNotReportedAsAbsent() throws Exception {
        mvc.perform(get(path()).header("Authorization", "Bearer " + token("lab-service", UUID.randomUUID(), "DOCTOR"))
                        .header("X-Correlation-Id", CORRELATION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CLINICAL_LAB_ORDER_ACTOR_MISMATCH"));
    }

    @Test
    void get_databaseFailureIsUnavailableRatherThanAbsence() throws Exception {
        when(contexts.get(ORDER)).thenThrow(new DataAccessResourceFailureException("private sql"));
        mvc.perform(get(path()).header("Authorization", "Bearer " + token("lab-service", STAFF, "DOCTOR"))
                        .header("X-Correlation-Id", CORRELATION))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("CLINICAL_LAB_ORDER_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private sql"))));
    }

    @Test
    void get_rejectsHumanPharmacyAndIncompleteServiceTokens() throws Exception {
        mvc.perform(get(path()).header("Authorization", "Bearer " + token("human", STAFF, "DOCTOR", "access"))
                        .header("X-Correlation-Id", CORRELATION)).andExpect(status().isUnauthorized());
        mvc.perform(get(path()).header("Authorization", "Bearer " + token("pharmacy-service", STAFF, "DOCTOR"))
                        .header("X-Correlation-Id", CORRELATION)).andExpect(status().isUnauthorized());
        mvc.perform(get(path()).header("Authorization", "Bearer " + token("lab-service", null, "DOCTOR"))
                        .header("X-Correlation-Id", CORRELATION)).andExpect(status().isUnauthorized());
        verifyNoInteractions(contexts);
    }

    @Test
    void get_requiresBoundedCorrelationAndRejectsHumanMutationVerb() throws Exception {
        mvc.perform(get(path()).header("Authorization", "Bearer " + token("lab-service", STAFF, "DOCTOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path())
                        .header("Authorization", "Bearer " + token("lab-service", STAFF, "DOCTOR"))
                        .header("X-Correlation-Id", CORRELATION))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(contexts);
    }

    private static String path() { return "/api/v1/lab-orders/" + ORDER + "/context"; }

    private static String token(String subject, UUID staffId, String actorRole) {
        return token(subject, staffId, actorRole, "service");
    }

    private static String token(String subject, UUID staffId, String actorRole, String type) {
        Instant issuedAt = Instant.now().minusSeconds(1);
        var builder = Jwts.builder().subject(subject).claim("role", "service".equals(type) ? "SYSTEM" : actorRole)
                .claim("type", type).issuedAt(Date.from(issuedAt)).expiration(Date.from(issuedAt.plusSeconds(60)));
        if (staffId != null) builder.claim("staffId", staffId.toString());
        if (actorRole != null) builder.claim("originalActorRole", actorRole);
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
