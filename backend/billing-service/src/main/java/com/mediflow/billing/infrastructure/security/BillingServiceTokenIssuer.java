package com.mediflow.billing.infrastructure.security;

import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class BillingServiceTokenIssuer {
    private final javax.crypto.SecretKey key;
    private final Clock clock;

    @Autowired
    public BillingServiceTokenIssuer(JwtProperties properties) { this(properties, Clock.systemUTC()); }

    BillingServiceTokenIssuer(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String issue() {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder().subject("billing-service")
                .claim(JwtClaims.ROLE, "SYSTEM")
                .claim(JwtClaims.TYPE, JwtClaims.SERVICE_TOKEN_TYPE)
                .issuedAt(Date.from(issuedAt)).expiration(Date.from(issuedAt.plusSeconds(60)))
                .signWith(key).compact();
    }
}
