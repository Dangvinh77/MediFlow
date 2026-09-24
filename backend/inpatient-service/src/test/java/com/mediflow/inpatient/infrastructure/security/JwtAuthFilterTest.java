package com.mediflow.inpatient.infrastructure.security;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtAuthFilterTest {

    private static final String SECRET = "inpatient-test-secret-at-least-32-bytes";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8));
    private final JwtAuthFilter filter = new JwtAuthFilter(new JwtProperties(SECRET));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_authenticatesSubjectAndRole() throws Exception {
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, Instant.now().plusSeconds(300))),
                new MockHttpServletResponse(), mock(FilterChain.class));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("doctor-01");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_DOCTOR");
    }

    @Test
    void expiredToken_doesNotAuthenticateRequest() throws Exception {
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, Instant.now().minusSeconds(30))),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidSignature_doesNotAuthenticateRequest() throws Exception {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-test-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
        filter.doFilter(requestWithToken(token(
                        otherKey, Instant.now().plusSeconds(300))),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void refreshToken_doesNotAuthenticateRequest() throws Exception {
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, Instant.now().plusSeconds(300),
                        JwtClaims.REFRESH_TOKEN_TYPE, "DOCTOR")),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void serviceToken_doesNotAuthenticatePublicApiRequest() throws Exception {
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, Instant.now().plusSeconds(300),
                        JwtClaims.SERVICE_TOKEN_TYPE, "SYSTEM")),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingType_doesNotAuthenticateRequest() throws Exception {
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, Instant.now().plusSeconds(300), null, "DOCTOR")),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unknownRole_doesNotAuthenticateRequest() throws Exception {
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, Instant.now().plusSeconds(300),
                        JwtClaims.ACCESS_TOKEN_TYPE, "SUPERUSER")),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingExpiration_doesNotAuthenticateRequest() throws Exception {
        filter.doFilter(requestWithToken(token(
                        SIGNING_KEY, null, JwtClaims.ACCESS_TOKEN_TYPE, "DOCTOR")),
                new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private String token(SecretKey signingKey, Instant expiresAt) {
        return token(signingKey, expiresAt, JwtClaims.ACCESS_TOKEN_TYPE, "DOCTOR");
    }

    private String token(
            SecretKey signingKey, Instant expiresAt, String type, String role) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject("doctor-01")
                .claim(JwtClaims.ROLE, role)
                .issuedAt(Date.from(now.minusSeconds(5)));
        if (type != null) {
            builder.claim(JwtClaims.TYPE, type);
        }
        if (expiresAt != null) {
            builder.expiration(Date.from(expiresAt));
        }
        return builder.signWith(signingKey).compact();
    }
}
