package com.mediflow.lab.infrastructure.client;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.lab.application.port.out.LabReleaseAuthorityPort;
import com.mediflow.lab.infrastructure.client.LabReleaseAuthorityFeignClient.ReleaseAuthorityBody;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Fails closed: a timeout, an error or an answer about another person never allows a release. */
public final class LabReleaseAuthorityAdapter implements LabReleaseAuthorityPort {
    private final LabReleaseAuthorityFeignClient client;
    private final Clock clock;
    private final SecretKey key;
    private final CircuitBreaker circuitBreaker;

    public LabReleaseAuthorityAdapter(LabReleaseAuthorityFeignClient client, Clock clock, String jwtSecret,
            CircuitBreaker circuitBreaker) {
        this.client = client;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public boolean canRelease(UUID actorAccountId, UUID actorStaffId, String correlationId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Release authority must be checked outside a Lab transaction");
        }
        ApiResponse<ReleaseAuthorityBody> response;
        try {
            String authorization = "Bearer " + serviceToken();
            response = circuitBreaker.executeSupplier(() -> client.authority(actorAccountId, authorization,
                    correlationId));
        } catch (RuntimeException unavailable) {
            return false;
        }
        if (response == null || !response.success() || response.data() == null) {
            return false;
        }
        ReleaseAuthorityBody body = response.data();
        return actorAccountId.equals(body.actorAccountId()) && actorStaffId != null
                && actorStaffId.equals(body.staffId()) && Boolean.TRUE.equals(body.accountActive())
                && Boolean.TRUE.equals(body.staffActive()) && Boolean.TRUE.equals(body.canReleaseLabResults());
    }

    private String serviceToken() {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder()
                .subject("lab-service")
                .claim(JwtClaims.ROLE, "SYSTEM")
                .claim(JwtClaims.TYPE, JwtClaims.SERVICE_TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(key)
                .compact();
    }
}
