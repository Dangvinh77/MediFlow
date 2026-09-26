package com.mediflow.pharmacy.infrastructure.security;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
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
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtAuthFilter filter = new JwtAuthFilter(new JwtProperties(SECRET));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_createsAuthenticationWithRoleAuthority() throws Exception {
        String accountId = "00000000-0000-0000-0000-000000000004";
        String token = createToken(accountId, "PHARMACIST", JwtClaims.ACCESS_TOKEN_TYPE,
                Instant.now().plusSeconds(300));
        MockHttpServletRequest request = requestWithToken(token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(accountId);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_PHARMACIST");
    }

    @Test
    void expiredToken_leavesRequestUnauthenticated() throws Exception {
        String token = createToken(UUID.randomUUID().toString(), "PHARMACIST",
                JwtClaims.ACCESS_TOKEN_TYPE, Instant.now().minusSeconds(60));

        filter.doFilter(
                requestWithToken(token),
                new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidSignature_leavesRequestUnauthenticated() throws Exception {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "different-test-secret-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtClaims.ROLE, "PHARMACIST")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(otherKey)
                .compact();

        filter.doFilter(
                requestWithToken(token),
                new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validToken_preservesDistinctAccountAndStaffIdentity() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String token = Jwts.builder()
                .subject(accountId.toString())
                .claim(JwtClaims.ROLE, "DOCTOR")
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE)
                .claim("staffId", staffId.toString())
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(SIGNING_KEY)
                .compact();

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal).isInstanceOf(ActorIdentity.class);
        ActorIdentity actor = (ActorIdentity) principal;
        assertThat(actor.accountId()).isEqualTo(accountId);
        assertThat(actor.staffId()).isEqualTo(staffId);
        assertThat(actor.auditActorId()).isEqualTo(staffId);
    }

    @Test
    void malformedStaffClaim_leavesRequestUnauthenticated() throws Exception {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtClaims.ROLE, "DOCTOR")
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE)
                .claim("staffId", "not-a-uuid")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(SIGNING_KEY)
                .compact();

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void refreshToken_leavesRequestUnauthenticated() throws Exception {
        assertRejectedTokenType(JwtClaims.REFRESH_TOKEN_TYPE);
    }

    @Test
    void serviceToken_leavesRequestUnauthenticated() throws Exception {
        assertRejectedTokenType(JwtClaims.SERVICE_TOKEN_TYPE);
    }

    @Test
    void missingTokenType_leavesRequestUnauthenticated() throws Exception {
        String token = createToken(UUID.randomUUID().toString(), "PHARMACIST", null,
                Instant.now().plusSeconds(300));

        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private void assertRejectedTokenType(String tokenType) throws Exception {
        String token = createToken(UUID.randomUUID().toString(), "PHARMACIST", tokenType,
                Instant.now().plusSeconds(300));
        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private String createToken(String subject, String role, String tokenType, Instant expiresAt) {
        var builder = Jwts.builder()
                .subject(subject)
                .claim(JwtClaims.ROLE, role)
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(expiresAt));
        if (tokenType != null) {
            builder.claim(JwtClaims.TYPE, tokenType);
        }
        return builder.signWith(SIGNING_KEY).compact();
    }
}
