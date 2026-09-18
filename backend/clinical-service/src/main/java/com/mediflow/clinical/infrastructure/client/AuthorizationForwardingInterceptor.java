package com.mediflow.clinical.infrastructure.client;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.infrastructure.correlation.CorrelationIdRequestAttribute;
import com.mediflow.clinical.infrastructure.security.JwtProperties;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.security.Roles;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Adds a short-lived service credential and correlation metadata to internal lookups. */
@Component
public class AuthorizationForwardingInterceptor implements RequestInterceptor {

    private static final String SERVICE_SUBJECT = "clinical-service";
    private static final long TOKEN_TTL_SECONDS = 60;

    private final CorrelationIdProvider correlationIds;
    private final SecretKey signingKey;
    private final Clock clock;

    @Autowired
    public AuthorizationForwardingInterceptor(
            CorrelationIdProvider correlationIds,
            JwtProperties properties) {
        this(correlationIds, properties, Clock.systemUTC());
    }

    AuthorizationForwardingInterceptor(
            CorrelationIdProvider correlationIds,
            JwtProperties properties,
            Clock clock) {
        this.correlationIds = correlationIds;
        this.signingKey = Keys.hmacShaKeyFor(
                properties.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = currentRequestAttributes();
        UUID correlationId = CorrelationIdRequestAttribute.read(attributes);
        if (correlationId == null) {
            correlationId = correlationIds.currentOrCreate();
        }
        template.header(JwtClaims.HEADER_CORRELATION_ID, correlationId.toString());
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + issueServiceToken());
    }

    private String issueServiceToken() {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder()
                .subject(SERVICE_SUBJECT)
                .claim(JwtClaims.ROLE, Roles.SYSTEM)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(TOKEN_TTL_SECONDS)))
                .signWith(signingKey)
                .compact();
    }

    private ServletRequestAttributes currentRequestAttributes() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes;
        }
        return null;
    }
}
