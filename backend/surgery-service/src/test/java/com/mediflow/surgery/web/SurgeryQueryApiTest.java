package com.mediflow.surgery.web;

import com.mediflow.common.api.*;
import com.mediflow.surgery.application.port.in.QuerySurgeryCasesUseCase;
import com.mediflow.surgery.domain.exception.SurgeryCaseNotFoundException;
import com.mediflow.surgery.infrastructure.config.SecurityConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SurgeryQueryController.class, properties = {
        "mediflow.features.surgery.enabled=true", "mediflow.jwt.secret=query-test-secret-with-at-least-32-bytes"})
@Import(SecurityConfig.class)
class SurgeryQueryApiTest {
    private static final String SECRET = "query-test-secret-with-at-least-32-bytes";
    @Autowired MockMvc mvc;
    @MockBean QuerySurgeryCasesUseCase queries;

    @ParameterizedTest @ValueSource(strings = {"ADMIN","MANAGER","DOCTOR","NURSE"})
    void boardAllowsOnlyDocumentedReaders(String role) throws Exception {
        when(queries.list(any(),any(),anyString())).thenReturn(PageResult.empty(PageQuery.of(0,20)));
        mvc.perform(get("/api/v1/surgery/cases").header("Authorization","Bearer " + token(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty()).andExpect(jsonPath("$.data.size").value(20));
    }

    @ParameterizedTest @ValueSource(strings = {"PATIENT","PHARMACIST","CASHIER","LAB_TECH"})
    void forbiddenReadersNeverInvokeUseCase(String role) throws Exception {
        mvc.perform(get("/api/v1/surgery/cases").header("Authorization","Bearer " + token(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queries);
    }

    @Test void unauthenticatedReadNeverInvokesUseCase() throws Exception {
        mvc.perform(get("/api/v1/surgery/cases")).andExpect(status().isUnauthorized());
        verifyNoInteractions(queries);
    }

    @Test void malformedStatusAndUuidUseErrorEnvelope() throws Exception {
        mvc.perform(get("/api/v1/surgery/cases").param("status","UNKNOWN")
                .header("Authorization","Bearer " + token("ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/surgery/cases/not-a-uuid").header("Authorization","Bearer " + token("ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(queries);
    }

    @Test void absentOrOutOfScopeDetailIs404NotSuccessNull() throws Exception {
        when(queries.detail(any(),any(),anyString())).thenThrow(new SurgeryCaseNotFoundException(UUID.randomUUID()));
        mvc.perform(get("/api/v1/surgery/cases/{id}",UUID.randomUUID()).header("Authorization","Bearer " + token("DOCTOR")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("SURGERY_CASE_NOT_FOUND"));
    }

    @Test void actorComesFromSignedClaimsNotHeaders() throws Exception {
        when(queries.list(any(),any(),anyString())).thenReturn(PageResult.empty(PageQuery.of(0,20)));
        mvc.perform(get("/api/v1/surgery/cases").header("Authorization","Bearer " + token("NURSE"))
                .header("X-User-Id", UUID.randomUUID()).header("X-Staff-Id",UUID.randomUUID()))
                .andExpect(status().isOk());
        verify(queries).list(any(),argThat(viewer -> viewer.accountId().equals(
                UUID.fromString("00000000-0000-0000-0000-000000000001")) && viewer.staffId().equals(
                UUID.fromString("00000000-0000-0000-0000-000000000002"))),anyString());
    }

    private String token(String role) {
        return Jwts.builder().subject("00000000-0000-0000-0000-000000000001")
                .claim("staffId","00000000-0000-0000-0000-000000000002").claim("type","access").claim("role",role)
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
