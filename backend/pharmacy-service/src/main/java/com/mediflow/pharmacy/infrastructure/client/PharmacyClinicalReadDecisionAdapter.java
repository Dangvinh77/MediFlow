package com.mediflow.pharmacy.infrastructure.client;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.port.out.ClinicalReadDecisionPort;
import com.mediflow.pharmacy.infrastructure.client.PharmacyClinicalReadDecisionFeignClient.ReadDecisionBody;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Any failure or unusable answer from Clinical becomes the existing Pharmacy 503, never an allowed read. */
public final class PharmacyClinicalReadDecisionAdapter implements ClinicalReadDecisionPort {
    private final PharmacyClinicalReadDecisionFeignClient client;
    private final Clock clock;
    private final SecretKey key;
    private final CircuitBreaker circuitBreaker;

    public PharmacyClinicalReadDecisionAdapter(PharmacyClinicalReadDecisionFeignClient client, Clock clock,
            String jwtSecret, CircuitBreaker circuitBreaker) {
        this.client = client;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public ClinicalReadDecision decide(UUID patientId, ActorIdentity actor, String correlationId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Clinical read decisions must run outside a Pharmacy transaction");
        }
        ApiResponse<ReadDecisionBody> response;
        try {
            String authorization = "Bearer " + serviceToken();
            response = circuitBreaker.executeSupplier(() -> client.decide(patientId, actor.accountId(),
                    actor.staffId(), actor.role(), authorization, correlationId));
        } catch (RuntimeException unavailable) {
            throw new PharmacyUpstreamUnavailableException();
        }
        if (response == null || !response.success() || response.data() == null
                || !patientId.equals(response.data().patientId()) || response.data().allowed() == null) {
            throw new PharmacyUpstreamUnavailableException();
        }
        return new ClinicalReadDecision(response.data().allowed(), response.data().denialCode());
    }

    private String serviceToken() {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder()
                .subject("pharmacy-service")
                .claim(JwtClaims.ROLE, "SYSTEM")
                .claim(JwtClaims.TYPE, JwtClaims.SERVICE_TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(key)
                .compact();
    }
}
