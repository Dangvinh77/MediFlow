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

    private final SecretKey key;
    private final JwtProperties props;

    public JwtTokenService(JwtProperties props) {
        this.props = Objects.requireNonNull(props, "props is required");
        this.key = Keys.hmacShaKeyFor(
                props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(UUID userId, String role) {
        return build(
                userId,
                role,
                props.accessTokenMinutes() * 60);
    }

    public String issueRefreshToken(UUID userId, String role) {
        return build(
                userId,
                role,
                props.refreshTokenMinutes() * 60);
    }

    private String build(
            UUID userId,
            String role,
            long ttlSeconds) {

        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(role, "role is required");

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId.toString())
                .claim(JwtClaims.ROLE, role)
                .claim(
                        JwtClaims.CORRELATION_ID,
                        UUID.randomUUID().toString())
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
