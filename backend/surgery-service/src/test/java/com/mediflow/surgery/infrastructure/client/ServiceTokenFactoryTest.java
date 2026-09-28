package com.mediflow.surgery.infrastructure.client;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.security.Roles;
import com.mediflow.surgery.infrastructure.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenFactoryTest {

    @Test
    void bearerToken_usesServiceIdentityAndSixtySecondLifetime() {
        String secret = "surgery-test-secret-at-least-32-bytes";
        Instant now = Instant.parse("2026-09-28T12:00:00Z");
        ServiceTokenFactory factory = new ServiceTokenFactory(
                new JwtProperties(secret), Clock.fixed(now, ZoneOffset.UTC));

        String bearer = factory.bearerToken();
        assertThat(bearer).startsWith("Bearer ");
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(bearer.substring(7)).getPayload();
        assertThat(claims.getSubject()).isEqualTo("surgery-service");
        assertThat(claims.get(JwtClaims.TYPE)).isEqualTo(JwtClaims.SERVICE_TOKEN_TYPE);
        assertThat(claims.get(JwtClaims.ROLE)).isEqualTo(Roles.SYSTEM);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(now.plusSeconds(60));
    }
}
