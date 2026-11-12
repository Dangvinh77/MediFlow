package com.mediflow.lab.infrastructure.client;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.lab.application.port.out.PatientDemographicsPort;
import com.mediflow.lab.infrastructure.client.LabPatientDemographicsFeignClient.DemographicsBody;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Reads sex and date of birth from Patient for reference ranges. Every failure or doubtful answer is
 * "unknown", which only leaves bounded flags UNDETERMINED; it never blocks a release.
 */
public final class LabPatientDemographicsAdapter implements PatientDemographicsPort {
    private final LabPatientDemographicsFeignClient client;
    private final Clock clock;
    private final SecretKey key;
    private final CircuitBreaker circuitBreaker;

    public LabPatientDemographicsAdapter(LabPatientDemographicsFeignClient client, Clock clock, String jwtSecret,
            CircuitBreaker circuitBreaker) {
        this.client = client;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Optional<PatientDemographics> find(UUID patientId, String correlationId) {
        ApiResponse<DemographicsBody> response;
        try {
            String authorization = "Bearer " + serviceToken();
            response = circuitBreaker.executeSupplier(() -> client.demographics(patientId, authorization,
                    correlationId));
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
        if (response == null || !response.success() || response.data() == null
                || !patientId.equals(response.data().maBenhNhan())) {
            return Optional.empty();
        }
        DemographicsBody body = response.data();
        return Optional.of(new PatientDemographics(sex(body.gioiTinh()), body.ngaySinh()));
    }

    /** Patient's locked gender codes M / F; reference ranges use MALE / FEMALE. */
    private static String sex(String gender) {
        if ("M".equals(gender)) {
            return "MALE";
        }
        return "F".equals(gender) ? "FEMALE" : null;
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
