package com.mediflow.gateway.security;

import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Issues and validates MediFlow JWTs using HS256.
 *
 * <p>The JWT subject always contains the authenticated user's UUID.
 */
@Service
public class JwtTokenService {

    public static final String DEPARTMENT_ID_CLAIM = "departmentId";

    private final SecretKey key;
    private final JwtProperties props;

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid username or password");
        }
    }

    public static class UpstreamUnavailableException extends RuntimeException {
        public UpstreamUnavailableException(String message) {
            super(message);
        }

        public UpstreamUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public JwtTokenService(JwtProperties props) {
        this.props = Objects.requireNonNull(props, "props is required");
        this.key = Keys.hmacShaKeyFor(
                props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(UUID userId, String role) {
        return issueAccessToken(userId, role, null);
    }

    public String issueAccessToken(UUID userId, String role, UUID departmentId) {
        return build(
                userId.toString(),
                role,
                props.accessTokenMinutes() * 60,
                departmentId,
                UUID.randomUUID().toString());
    }

    public String issueRefreshToken(UUID userId, String role) {
        return issueRefreshToken(userId, role, null);
    }

    public String issueRefreshToken(UUID userId, String role, UUID departmentId) {
        return build(
                userId.toString(),
                role,
                props.refreshTokenMinutes() * 60,
                departmentId,
                UUID.randomUUID().toString());
    }

    public String issueServiceToken(
            String subject,
            String role,
            String correlationId) {
        return build(
                subject,
                role,
                props.serviceTokenMinutes() * 60,
                null,
                correlationId);
    }

    private String build(
            String subject,
            String role,
            long ttlSeconds,
            UUID departmentId,
            String correlationId) {

        Objects.requireNonNull(subject, "subject is required");
        Objects.requireNonNull(role, "role is required");

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(subject)
                .claim(JwtClaims.ROLE, role)
                .claim(
                        JwtClaims.CORRELATION_ID,
                        correlationId == null || correlationId.isBlank()
                                ? UUID.randomUUID().toString()
                                : correlationId)
                .claim(DEPARTMENT_ID_CLAIM, departmentId == null ? null : departmentId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(
                        now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates a signed JWT.
     *
     * @param token encoded JWT
     * @return validated claims
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
