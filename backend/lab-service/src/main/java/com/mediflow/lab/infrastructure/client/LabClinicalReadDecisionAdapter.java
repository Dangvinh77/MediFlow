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
import com.mediflow.lab.application.exception.LabUpstreamUnavailableException;
import com.mediflow.lab.application.port.out.ClinicalReadDecisionPort;
import com.mediflow.lab.application.port.out.ReaderIdentity;
import com.mediflow.lab.infrastructure.client.LabClinicalReadDecisionFeignClient.ReadDecisionBody;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Any failure or unusable answer from Clinical becomes the existing Lab 503, never an allowed read. */
public final class LabClinicalReadDecisionAdapter implements ClinicalReadDecisionPort {
    private final LabClinicalReadDecisionFeignClient client;
    private final Clock clock;
    private final SecretKey key;
    private final CircuitBreaker circuitBreaker;

    public LabClinicalReadDecisionAdapter(LabClinicalReadDecisionFeignClient client, Clock clock, String jwtSecret,
            CircuitBreaker circuitBreaker) {
        this.client = client;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public ClinicalReadDecision decide(UUID patientId, ReaderIdentity reader, String correlationId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Clinical read decisions must run outside a Lab transaction");
        }
        ApiResponse<ReadDecisionBody> response;
        try {
            String authorization = "Bearer " + serviceToken();
            response = circuitBreaker.executeSupplier(() -> client.decide(patientId, reader.accountId(),
                    reader.staffId(), reader.role(), authorization, correlationId));
        } catch (RuntimeException unavailable) {
            throw new LabUpstreamUnavailableException();
        }
        if (response == null || !response.success() || response.data() == null
                || !patientId.equals(response.data().patientId()) || response.data().allowed() == null) {
            throw new LabUpstreamUnavailableException();
        }
        return new ClinicalReadDecision(response.data().allowed(), response.data().denialCode());
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
