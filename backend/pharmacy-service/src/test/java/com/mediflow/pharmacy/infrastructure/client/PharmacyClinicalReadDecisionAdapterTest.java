package com.mediflow.pharmacy.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.infrastructure.client.PharmacyClinicalReadDecisionFeignClient.ReadDecisionBody;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class PharmacyClinicalReadDecisionAdapterTest {
    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    private static final Instant NOW = Instant.now();

    private final PharmacyClinicalReadDecisionFeignClient client = mock(PharmacyClinicalReadDecisionFeignClient.class);
    private final PharmacyClinicalReadDecisionAdapter adapter = new PharmacyClinicalReadDecisionAdapter(client,
            Clock.fixed(NOW, ZoneOffset.UTC), SECRET, CircuitBreaker.ofDefaults("test"));
    private final UUID patientId = UUID.randomUUID();
    private final ActorIdentity doctor = new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), "DOCTOR");

    @Test
    void sendsReaderIdentityUnderShortLivedServiceToken() {
        when(client.decide(eq(patientId), eq(doctor.accountId()), eq(doctor.staffId()), eq("DOCTOR"), anyString(), eq("corr-1")))
                .thenReturn(ApiResponse.ok(new ReadDecisionBody(patientId, false, null, "RECORD_ACCESS_DENIED", NOW), "corr-1"));

        var decision = adapter.decide(patientId, doctor, "corr-1");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denialCode()).isEqualTo("RECORD_ACCESS_DENIED");
        ArgumentCaptor<String> authorization = ArgumentCaptor.forClass(String.class);
        verify(client).decide(any(), any(), any(), any(), authorization.capture(), any());
        Claims claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).build()
                .parseSignedClaims(authorization.getValue().substring("Bearer ".length())).getPayload();
        assertThat(claims.getSubject()).isEqualTo("pharmacy-service");
        assertThat(claims.get(JwtClaims.ROLE, String.class)).isEqualTo("SYSTEM");
        assertThat(claims.get(JwtClaims.TYPE, String.class)).isEqualTo(JwtClaims.SERVICE_TOKEN_TYPE);
    }

    @Test
    void clientFailureBecomesUpstreamUnavailable() {
        when(client.decide(any(), any(), any(), any(), any(), any())).thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> adapter.decide(patientId, doctor, "corr-1"))
                .isInstanceOf(PharmacyUpstreamUnavailableException.class);
    }

    @Test
    void answerForAnotherPatientIsRejected() {
        when(client.decide(any(), any(), any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(new ReadDecisionBody(UUID.randomUUID(), true, "ASSIGNED", null, NOW), "corr-1"));

        assertThatThrownBy(() -> adapter.decide(patientId, doctor, "corr-1"))
                .isInstanceOf(PharmacyUpstreamUnavailableException.class);
    }
}
