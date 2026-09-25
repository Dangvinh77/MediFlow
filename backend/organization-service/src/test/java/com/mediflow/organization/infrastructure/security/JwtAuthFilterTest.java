package com.mediflow.organization.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
    void expiredToken_leavesRequestUnauthenticated() throws Exception {
        String token = createToken("clinical-service", "SYSTEM", JwtClaims.SERVICE_TOKEN_TYPE,
                Instant.now().minusSeconds(60));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void refreshToken_isNotAcceptedAsServiceAuthentication() throws Exception {
        String token = createToken("clinical-service", "SYSTEM", JwtClaims.REFRESH_TOKEN_TYPE,
                Instant.now().plusSeconds(300));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private String createToken(String subject, String role, String type, Instant expiresAt) {
        return Jwts.builder()
                .subject(subject)
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.TYPE, type)
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(expiresAt))
                .signWith(SIGNING_KEY)
                .compact();
    }
}
