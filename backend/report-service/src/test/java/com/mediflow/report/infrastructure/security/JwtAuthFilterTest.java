package com.mediflow.report.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.time.Instant;
import java.util.stream.Stream;
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

    @Test
    void accessTokenWithoutExpiration_isRejectedOnOutpatientReportPath() throws Exception {
        String accountId = UUID.randomUUID().toString();
        MockHttpServletRequest request = requestWithToken(tokenWithoutExpiration(accountId));
        request.setRequestURI("/api/v1/reports/outpatient/daily");

        new JwtAuthFilter(new JwtProperties(SECRET)).doFilter(request,
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    static Stream<Arguments> humanRoutesAndRoles() {
        return Stream.of(
                "/api/v1/reports/outpatient/daily",
                "/api/v1/reports/outpatient/monthly",
                "/api/v1/reports/daily",
                "/api/v1/reports/monthly",
                "/api/v1/reports/top-medicines",
                "/api/v1/reports/financial/daily",
                "/api/v1/reports/operations/daily")
                .flatMap(path -> Stream.of("ADMIN", "MANAGER")
                        .map(role -> Arguments.of(path, role)));
    }

    @ParameterizedTest
    @MethodSource("humanRoutesAndRoles")
    void missingExpiration_isRejectedOnEveryHumanRoute(String path, String role) throws Exception {
        assertAuthentication(path, credential(role, "access", null, KEY), false);
    }

    @ParameterizedTest
    @MethodSource("humanRoutesAndRoles")
    void expiredAccess_isRejectedOnEveryHumanRoute(String path, String role) throws Exception {
        assertAuthentication(path, credential(role, "access", Instant.now().minusSeconds(60), KEY), false);
    }

    @ParameterizedTest
    @MethodSource("humanRoutesAndRoles")
    void unexpiredAccess_preservesRoleAndSubject(String path, String role) throws Exception {
        assertAuthentication(path, credential(role, "access", Instant.now().plusSeconds(300), KEY), true);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_" + role);
    }

    @ParameterizedTest
    @MethodSource("humanRoutesAndRoles")
    void wrongSignature_isRejectedWithOtherwiseValidAccessClaims(String path, String role) throws Exception {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "different-test-secret-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
        assertAuthentication(path, credential(role, "access", Instant.now().plusSeconds(300), otherKey), false);
    }

    @ParameterizedTest
    @MethodSource("humanRoutesAndRoles")
    void wrongTokenTypes_remainRejectedWithExpiration(String path, String role) throws Exception {
        for (String type : new String[]{"refresh", "service"}) {
            assertAuthentication(path, credential(role, type, Instant.now().plusSeconds(300), KEY), false);
        }
    }

    @Test
    void malformedSubject_remainsRejectedOnGuardedReportPaths() throws Exception {
        assertAuthentication("/api/v1/reports/financial/daily", token("access"), false);
        assertAuthentication("/api/v1/reports/operations/daily", token("access"), false);
    }

    private String credential(String role, String type, Instant expiration, SecretKey key) {
        var builder = Jwts.builder().subject("00000000-0000-4000-8000-000000000001")
                .claim(JwtClaims.ROLE, role).claim(JwtClaims.TYPE, type);
        if (expiration != null) builder.expiration(Date.from(expiration));
        return builder.signWith(key).compact();
    }

    private void assertAuthentication(String path, String token, boolean expected) throws Exception {
        MockHttpServletRequest request = requestWithToken(token);
        request.setRequestURI(path);
        new JwtAuthFilter(new JwtProperties(SECRET)).doFilter(request,
                new MockHttpServletResponse(), new MockFilterChain());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (expected) {
            assertThat(authentication).isNotNull();
            assertThat(authentication.getName()).isEqualTo("00000000-0000-4000-8000-000000000001");
        } else {
            assertThat(authentication).isNull();
        }
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

    private String tokenWithoutExpiration(String accountId) {
        return Jwts.builder()
                .subject(accountId)
                .claim(JwtClaims.ROLE, "ADMIN")
                .claim(JwtClaims.TYPE, "access")
                .issuedAt(new Date())
                .signWith(KEY)
                .compact();
    }
}
