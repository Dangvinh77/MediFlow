package com.mediflow.report.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mediflow.common.security.JwtClaims;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshTokenType_isRejectedEvenWhenSignatureAndRoleAreValid() throws Exception {
        MockHttpServletRequest request = requestWithToken(token("refresh"));

        new JwtAuthFilter(new JwtProperties(SECRET)).doFilter(request,
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void serviceTokenType_isRejectedEvenWhenSignatureAndRoleAreValid() throws Exception {
        MockHttpServletRequest request = requestWithToken(token("service"));

        new JwtAuthFilter(new JwtProperties(SECRET)).doFilter(request,
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingTokenType_isRejectedEvenWhenSignatureAndRoleAreValid() throws Exception {
        MockHttpServletRequest request = requestWithToken(tokenWithoutType());

        new JwtAuthFilter(new JwtProperties(SECRET)).doFilter(request,
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void accessTokenType_isAccepted() throws Exception {
        MockHttpServletRequest request = requestWithToken(token("access"));

        new JwtAuthFilter(new JwtProperties(SECRET)).doFilter(request,
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull()
                .extracting(authentication -> authentication.getName())
                .isEqualTo("user-1");
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private String token(String tokenType) {
        Date now = new Date();
        return Jwts.builder()
                .subject("user-1")
                .claim(JwtClaims.ROLE, "ADMIN")
                .claim(JwtClaims.TYPE, tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(KEY)
                .compact();
    }

    private String tokenWithoutType() {
        Date now = new Date();
        return Jwts.builder()
                .subject("user-1")
                .claim(JwtClaims.ROLE, "ADMIN")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(KEY)
                .compact();
    }
}
