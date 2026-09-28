package com.mediflow.surgery.infrastructure.client;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.security.Roles;
import com.mediflow.surgery.infrastructure.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** Issues a short lived credential for Surgery's service-only lookups. */
@Component
public final class ServiceTokenFactory {

    private static final String SUBJECT = "surgery-service";
    private static final long TTL_SECONDS = 60;

    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public ServiceTokenFactory(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ServiceTokenFactory(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String bearerToken() {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        String token = Jwts.builder()
                .subject(SUBJECT)
                .claim(JwtClaims.TYPE, JwtClaims.SERVICE_TOKEN_TYPE)
                .claim(JwtClaims.ROLE, Roles.SYSTEM)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(TTL_SECONDS)))
                .signWith(key)
                .compact();
        return "Bearer " + token;
    }
}
