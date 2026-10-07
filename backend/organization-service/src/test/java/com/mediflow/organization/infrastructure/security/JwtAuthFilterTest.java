package com.mediflow.organization.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mediflow.common.security.JwtClaims;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtAuthFilter filter = new JwtAuthFilter(new JwtProperties(SECRET));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validSystemToken_createsSystemAuthentication() throws Exception {
        String token = createToken("clinical-service", "SYSTEM", JwtClaims.SERVICE_TOKEN_TYPE,
                Instant.now().plusSeconds(300));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("clinical-service");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SYSTEM", "ROLE_SYSTEM_SERVICE");
    }

    @Test
    void validHumanAccessToken_createsHumanAuthentication() throws Exception {
        UUID accountId = UUID.randomUUID();
        String token = createToken(accountId.toString(), "DOCTOR", JwtClaims.ACCESS_TOKEN_TYPE,
                Instant.now().plusSeconds(300), Map.of(
                        JwtClaims.STAFF_ID, UUID.randomUUID().toString(),
                        JwtClaims.DEPARTMENT_ID, UUID.randomUUID().toString()));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(accountId.toString());
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_DOCTOR");
    }

    @Test
    void expiredToken_leavesRequestUnauthenticated() throws Exception {
        String token = createToken("clinical-service", "SYSTEM", JwtClaims.SERVICE_TOKEN_TYPE,
                Instant.now().minusSeconds(60));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void humanRefreshToken_isNotAcceptedAsAuthentication() throws Exception {
        String token = createToken(UUID.randomUUID().toString(), "ADMIN", JwtClaims.REFRESH_TOKEN_TYPE,
                Instant.now().plusSeconds(300));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void serviceTokenWithBlankSubject_isNotAccepted() throws Exception {
        String token = createToken("   ", "SYSTEM", JwtClaims.SERVICE_TOKEN_TYPE,
                Instant.now().plusSeconds(300));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void serviceTokenWithNonSystemRole_isNotAccepted() throws Exception {
        String token = createToken("clinical-service", "DOCTOR", JwtClaims.SERVICE_TOKEN_TYPE,
                Instant.now().plusSeconds(300));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {JwtClaims.STAFF_ID, JwtClaims.DEPARTMENT_ID, JwtClaims.PATIENT_ID})
    void serviceTokenWithHumanIdentityClaim_isNotAccepted(String claimName) throws Exception {
        String token = createToken("clinical-service", "SYSTEM", JwtClaims.SERVICE_TOKEN_TYPE,
                Instant.now().plusSeconds(300), Map.of(claimName, UUID.randomUUID().toString()));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void humanAccessTokenWithNonUuidSubject_isNotAccepted() throws Exception {
        String token = createToken("not-an-account-id", "ADMIN", JwtClaims.ACCESS_TOKEN_TYPE,
                Instant.now().plusSeconds(300));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void humanAccessTokenWithUnknownRole_isNotAccepted() throws Exception {
        String token = createToken(UUID.randomUUID().toString(), "AUDITOR", JwtClaims.ACCESS_TOKEN_TYPE,
                Instant.now().plusSeconds(300));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void patientAccessTokenWithoutPatientId_isNotAccepted() throws Exception {
        String token = createToken(UUID.randomUUID().toString(), "PATIENT", JwtClaims.ACCESS_TOKEN_TYPE,
                Instant.now().plusSeconds(300));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void patientAccessTokenWithStaffId_isNotAccepted() throws Exception {
        String token = createToken(UUID.randomUUID().toString(), "PATIENT", JwtClaims.ACCESS_TOKEN_TYPE,
                Instant.now().plusSeconds(300), Map.of(
                        JwtClaims.PATIENT_ID, UUID.randomUUID().toString(),
                        JwtClaims.STAFF_ID, UUID.randomUUID().toString()));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonPatientAccessTokenWithPatientId_isNotAccepted() throws Exception {
        String token = createToken(UUID.randomUUID().toString(), "DOCTOR", JwtClaims.ACCESS_TOKEN_TYPE,
                Instant.now().plusSeconds(300), Map.of(JwtClaims.PATIENT_ID, UUID.randomUUID().toString()));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tamperedToken_isNotAccepted() throws Exception {
        String token = createToken(UUID.randomUUID().toString(), "ADMIN", JwtClaims.ACCESS_TOKEN_TYPE,
                Instant.now().plusSeconds(300)) + "tampered";

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private String createToken(String subject, String role, String type, Instant expiresAt) {
        return createToken(subject, role, type, expiresAt, Map.of());
    }

    private String createToken(
            String subject,
            String role,
            String type,
            Instant expiresAt,
            Map<String, Object> extraClaims) {
        var builder = Jwts.builder()
                .subject(subject)
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.TYPE, type);
        extraClaims.forEach(builder::claim);
        return builder
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(expiresAt))
                .signWith(SIGNING_KEY)
                .compact();
    }
}
