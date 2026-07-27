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
import java.util.UUID;

/** Issues and validates MediFlow JWTs (HS256). */
@Service
public class JwtTokenService {

    private final SecretKey key;
    private final JwtProperties props;

    public JwtTokenService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(String userId, String role) {
        return build(userId, role, props.accessTokenMinutes() * 60);
    }

    public String issueRefreshToken(String userId, String role) {
        return build(userId, role, props.refreshTokenMinutes() * 60);
    }

    private String build(String userId, String role, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.CORRELATION_ID, UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /** @return parsed claims if the token is valid; throws JwtException otherwise. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
