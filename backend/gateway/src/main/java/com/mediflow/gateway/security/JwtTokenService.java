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
 * <p>Human JWT subjects contain the authenticated account UUID. Service JWT subjects identify the
 * calling service, while human patient identity is carried separately in {@code patientId}.
 */
@Service
public class JwtTokenService {

    public static final String DEPARTMENT_ID_CLAIM = JwtClaims.DEPARTMENT_ID;

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
        return issueAccessToken(userId, role, null, null, null);
    }

    public String issueAccessToken(UUID userId, String role, UUID departmentId) {
        return issueAccessToken(userId, role, null, departmentId, null);
    }

    public String issueAccessToken(
            UUID accountId,
            String role,
            UUID staffId,
            UUID departmentId,
            UUID patientId) {
        return build(
                accountId.toString(),
                role,
                JwtClaims.ACCESS_TOKEN_TYPE,
                props.accessTokenMinutes() * 60,
                staffId,
                departmentId,
                patientId,
                UUID.randomUUID().toString());
    }

    public String issueRefreshToken(UUID userId, String role) {
        return issueRefreshToken(userId, role, null, null, null);
    }

    public String issueRefreshToken(UUID userId, String role, UUID departmentId) {
        return issueRefreshToken(userId, role, null, departmentId, null);
    }

    public String issueRefreshToken(
            UUID accountId,
            String role,
            UUID staffId,
            UUID departmentId,
            UUID patientId) {
        return build(
                accountId.toString(),
                role,
                JwtClaims.REFRESH_TOKEN_TYPE,
                props.refreshTokenMinutes() * 60,
                staffId,
                departmentId,
                patientId,
                UUID.randomUUID().toString());
    }

    public String issueServiceToken(
            String subject,
            String role,
            String correlationId) {
        return build(
                subject,
                role,
                JwtClaims.SERVICE_TOKEN_TYPE,
                props.serviceTokenMinutes() * 60,
                null,
                null,
                null,
                correlationId);
    }

    private String build(
            String subject,
            String role,
            String tokenType,
            long ttlSeconds,
            UUID staffId,
            UUID departmentId,
            UUID patientId,
            String correlationId) {

        Objects.requireNonNull(subject, "subject is required");
        Objects.requireNonNull(role, "role is required");

        Instant now = Instant.now();

        var builder = Jwts.builder()
                .subject(subject)
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.TYPE, tokenType)
                .claim(
                        JwtClaims.CORRELATION_ID,
                        correlationId == null || correlationId.isBlank()
                                ? UUID.randomUUID().toString()
                                : correlationId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(
                        now.plusSeconds(ttlSeconds)));
        if (staffId != null) {
            builder.claim(JwtClaims.STAFF_ID, staffId.toString());
        }
        if (departmentId != null) {
            builder.claim(JwtClaims.DEPARTMENT_ID, departmentId.toString());
        }
        if (patientId != null) {
            builder.claim(JwtClaims.PATIENT_ID, patientId.toString());
        }
        return builder.signWith(key).compact();
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
