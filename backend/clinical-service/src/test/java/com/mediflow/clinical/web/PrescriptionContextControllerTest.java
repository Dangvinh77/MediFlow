package com.mediflow.clinical.web;

import com.mediflow.clinical.application.dto.response.PrescriptionContextDTO;
import com.mediflow.clinical.application.port.in.GetPrescriptionContextUseCase;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.infrastructure.config.SecurityConfig;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PrescriptionContextController.class, properties = {
        "mediflow.clinical.prescription-context-lookup.enabled=true", "mediflow.jwt.secret=clinical-context-test-secret-at-least-32-bytes"})
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class PrescriptionContextControllerTest {
    private static final String SECRET = "clinical-context-test-secret-at-least-32-bytes";
    private static final UUID ID = UUID.fromString("09000000-0000-0000-0000-000000000001");
    private static final String CORRELATION = "09000000-0000-0000-0000-000000000006";
    private static final String PATH = "/api/v1/records/" + ID + "/prescription-context";
    @Autowired MockMvc mvc;
    @MockBean GetPrescriptionContextUseCase contexts;
    @BeforeEach void prepare() { reset(contexts); when(contexts.get(ID)).thenReturn(new PrescriptionContextDTO(false, ID, null, null, null, null, null, null, null, Instant.now())); }
    @Test void get_serviceTokenOnlyReturnsConfirmedMissingAndCorrelation() throws Exception {
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token("pharmacy-service", "SYSTEM", "service", 60))
                .header("X-Correlation-Id", CORRELATION)).andExpect(status().isOk()).andExpect(header().string("X-Correlation-Id", CORRELATION))
                .andExpect(jsonPath("$.data.exists").value(false)).andExpect(jsonPath("$.data.recordId").value(ID.toString()))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION));
    }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DOCTOR", "SYSTEM"})
    void get_humanAccessCannotReadInternalContext(String role) throws Exception {
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token(UUID.randomUUID().toString(), role, "access", 60))
                .header("X-Correlation-Id", CORRELATION)).andExpect(status().isUnauthorized()); verifyNoInteractions(contexts);
    }
    @ParameterizedTest @ValueSource(strings = {"refresh", "other-service", "long-lived", "missing-issued"})
    void get_badCredentialCannotReadOrFallBackToHuman(String defect) throws Exception {
        String token = token(defect.equals("other-service") ? "surgery-service" : "pharmacy-service", "SYSTEM",
                defect.equals("refresh") ? "refresh" : "service", defect.equals("long-lived") ? 61 : defect.equals("missing-issued") ? 0 : 60);
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token).header("X-Correlation-Id", CORRELATION))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(contexts);
    }
    @Test void get_storageFailureIs503NotAbsenceAndDoesNotLeakSql() throws Exception {
        when(contexts.get(ID)).thenThrow(new DataAccessResourceFailureException("PRIVATE SQL"));
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token("pharmacy-service", "SYSTEM", "service", 60))
                .header("X-Correlation-Id", CORRELATION)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("CLINICAL_CONTEXT_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE"))));
    }
    @Test void get_missingCorrelationIs400AndDoesNotCallReadPort() throws Exception {
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token("pharmacy-service", "SYSTEM", "service", 60)))
                .andExpect(status().isBadRequest()); verifyNoInteractions(contexts);
    }
    @Test @org.springframework.security.test.context.support.WithMockUser(roles = "SYSTEM")
    void get_genericSystemAuthorityWithoutVerifiedServicePrincipalIsForbidden() throws Exception {
        mvc.perform(get(PATH).header("X-Correlation-Id", CORRELATION)).andExpect(status().isForbidden());
        verifyNoInteractions(contexts);
    }
    @Test void serviceTokenCannotCallHumanMutation() throws Exception {
        mvc.perform(post("/api/v1/records").header("Authorization", "Bearer " + token("pharmacy-service", "SYSTEM", "service", 60)))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(contexts);
    }
    @ParameterizedTest @ValueSource(strings = {"09000000-0000-0000-0000-00000000000A", "9-0-0-0-1"})
    void get_nonCanonicalPathCannotBypassInternalTokenGate(String id) throws Exception {
        mvc.perform(get("/api/v1/records/" + id + "/prescription-context")
                .header("Authorization", "Bearer " + token(UUID.randomUUID().toString(), "SYSTEM", "access", 60))
                .header("X-Correlation-Id", CORRELATION)).andExpect(status().isUnauthorized()); verifyNoInteractions(contexts);
    }
    private String token(String subject, String role, String type, int lifetime) {
        var now = Instant.now().minusSeconds(1);
        var builder = Jwts.builder().subject(subject).claim("role", role).claim("type", type)
                .expiration(Date.from(now.plusSeconds(lifetime == 0 ? 60 : lifetime)));
        if (lifetime != 0) builder.issuedAt(Date.from(now));
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
