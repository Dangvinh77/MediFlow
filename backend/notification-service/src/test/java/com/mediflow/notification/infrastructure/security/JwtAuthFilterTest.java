package com.mediflow.notification.infrastructure.security;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.notification.application.dto.command.CallerIdentity;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtAuthFilter filter = new JwtAuthFilter(new JwtProperties(SECRET));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void patientAccessToken_withPatientIdClaim_buildsCallerIdentityFromClaimNotSubject() throws Exception {
        String accountId = "11111111-1111-1111-1111-111111111111";
        String patientId = "22222222-2222-2222-2222-222222222222";
        String token = createToken(accountId, "PATIENT", JwtClaims.ACCESS_TOKEN_TYPE,
                SIGNING_KEY, Instant.now().plusSeconds(300), builder -> builder.claim(JwtClaims.PATIENT_ID, patientId));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(accountId);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_PATIENT");
        CallerIdentity identity = (CallerIdentity) authentication.getPrincipal();
        assertThat(identity.accountId()).isEqualTo(UUID.fromString(accountId));
        assertThat(identity.patientId()).isEqualTo(UUID.fromString(patientId));
    }

    @Test
    void patientAccessToken_withoutPatientIdClaim_leavesPatientIdNullInsteadOfFallingBackToSubject()
            throws Exception {
        String accountId = "11111111-1111-1111-1111-111111111111";
        String token = createToken(accountId, "PATIENT", JwtClaims.ACCESS_TOKEN_TYPE,
                SIGNING_KEY, Instant.now().plusSeconds(300), builder -> builder);

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        CallerIdentity identity = (CallerIdentity) authentication.getPrincipal();
        assertThat(identity.patientId()).isNull();
    }

    @Test
    void staffAccessToken_hasNullPatientIdRegardlessOfSubject() throws Exception {
        String accountId = "33333333-3333-3333-3333-333333333333";
        String token = createToken(accountId, "ADMIN", JwtClaims.ACCESS_TOKEN_TYPE,
                SIGNING_KEY, Instant.now().plusSeconds(300), builder -> builder);

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        CallerIdentity identity = (CallerIdentity) authentication.getPrincipal();
        assertThat(identity.patientId()).isNull();
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void serviceToken_withSystemRole_authenticatesWithNonUuidSubject() throws Exception {
        String token = createToken("billing-service", "SYSTEM", JwtClaims.SERVICE_TOKEN_TYPE,
                SIGNING_KEY, Instant.now().plusSeconds(60), builder -> builder);

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("billing-service");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_SYSTEM");
    }

    @Test
    void serviceToken_withNonSystemRole_leavesRequestUnauthenticated() throws Exception {
        String token = createToken("11111111-1111-1111-1111-111111111111", "ADMIN",
                JwtClaims.SERVICE_TOKEN_TYPE, SIGNING_KEY, Instant.now().plusSeconds(60), builder -> builder);

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void refreshToken_cannotAuthenticateHumanEndpoints() throws Exception {
        String token = createToken("11111111-1111-1111-1111-111111111111", "PATIENT",
                JwtClaims.REFRESH_TOKEN_TYPE, SIGNING_KEY, Instant.now().plusSeconds(300), builder -> builder);

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingTokenType_leavesRequestUnauthenticated() throws Exception {
        String token = Jwts.builder()
                .subject("11111111-1111-1111-1111-111111111111")
                .claim(JwtClaims.ROLE, "PATIENT")
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(SIGNING_KEY)
                .compact();

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredToken_leavesRequestUnauthenticated() throws Exception {
        String token = createToken("11111111-1111-1111-1111-111111111111", "ADMIN",
                JwtClaims.ACCESS_TOKEN_TYPE, SIGNING_KEY, Instant.now().minusSeconds(60), builder -> builder);

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidSignature_leavesRequestUnauthenticated() throws Exception {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "different-test-secret-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
        String token = createToken("11111111-1111-1111-1111-111111111111", "ADMIN",
                JwtClaims.ACCESS_TOKEN_TYPE, otherKey, Instant.now().plusSeconds(300), builder -> builder);

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingToken_leavesRequestUnauthenticated() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private String createToken(String subject, String role, String tokenType, SecretKey key,
            Instant expiresAt, java.util.function.UnaryOperator<JwtBuilder> customizer) {
        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.TYPE, tokenType)
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(expiresAt));
        return customizer.apply(builder).signWith(key).compact();
    }
}
