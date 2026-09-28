package com.mediflow.surgery.infrastructure.security;

import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtAuthFilterTest {

    private static final String SECRET = "surgery-test-secret-at-least-32-bytes";
    private static final UUID ACCOUNT_ID = UUID.fromString("9d2c0477-91e7-4e92-82ae-e18c82f11375");
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8));
    private final JwtAuthFilter filter = new JwtAuthFilter(new JwtProperties(SECRET));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validAccessToken_authenticatesHumanRoleAndExplicitIdentities() throws Exception {
        UUID staffId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, Instant.now().plusSeconds(300), JwtClaims.ACCESS_TOKEN_TYPE,
                        "DOCTOR", staffId, departmentId)),
                new MockHttpServletResponse(), mock(FilterChain.class));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(ACCOUNT_ID);
        assertThat(authentication.getName()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_DOCTOR");
        assertThat(authentication.getDetails()).isEqualTo(
                new SurgeryAuthenticationDetails(ACCOUNT_ID, staffId, null, departmentId));
    }

    @Test
    void refreshToken_doesNotAuthenticateRequest() throws Exception {
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, Instant.now().plusSeconds(300),
                        JwtClaims.REFRESH_TOKEN_TYPE, "DOCTOR", null, null)),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void serviceToken_doesNotAuthenticateHumanApi() throws Exception {
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, Instant.now().plusSeconds(300),
                        JwtClaims.SERVICE_TOKEN_TYPE, "SYSTEM", null, null)),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredToken_doesNotAuthenticateRequest() throws Exception {
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, Instant.now().minusSeconds(30), JwtClaims.ACCESS_TOKEN_TYPE,
                        "DOCTOR", null, null)),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unknownRole_doesNotAuthenticateRequest() throws Exception {
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, Instant.now().plusSeconds(300), JwtClaims.ACCESS_TOKEN_TYPE,
                        "SUPERUSER", null, null)),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonUuidAccountSubject_doesNotAuthenticateRequest() throws Exception {
        String token = token(
                SIGNING_KEY, Instant.now().plusSeconds(300), JwtClaims.ACCESS_TOKEN_TYPE,
                "DOCTOR", null, null, "not-an-account-uuid");
        filter.doFilter(requestWithToken(token),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private String token(
            SecretKey signingKey,
            Instant expiresAt,
            String type,
            String role,
            UUID staffId,
            UUID departmentId) {
        return token(signingKey, expiresAt, type, role, staffId, departmentId, ACCOUNT_ID.toString());
    }

    private String token(
            SecretKey signingKey,
            Instant expiresAt,
            String type,
            String role,
            UUID staffId,
            UUID departmentId,
            String subject) {

        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(subject)
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.TYPE, type)
                .issuedAt(Date.from(now.minusSeconds(5)))
                .expiration(Date.from(expiresAt));
        if (staffId != null) {
            builder.claim(JwtClaims.STAFF_ID, staffId.toString());
        }
        if (departmentId != null) {
            builder.claim(JwtClaims.DEPARTMENT_ID, departmentId.toString());
        }
        return builder.signWith(signingKey).compact();
    }
}
