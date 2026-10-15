package com.mediflow.lab.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.lab.application.exception.LabUpstreamUnavailableException;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LabClinicalOrderContextAdapterTest {
    private static final String SECRET = "lab-clinical-context-test-secret-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID DOCTOR = UUID.fromString("00000000-0000-0000-0000-000000001004");
    private static final String CORRELATION = "00000000-0000-0000-0000-000000001099";
    private static final String FIXTURE = "/contracts/clinical-lab-order-context-v1/valid.json";
    private final LabClinicalOrderFeignClient client = mock(LabClinicalOrderFeignClient.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CircuitBreaker breaker = CircuitBreaker.ofDefaults("clinical-lab-order-test");
    private final LabClinicalOrderContextAdapter adapter = adapter(breaker);
    private final AuthenticatedStaffContext actor = new AuthenticatedStaffContext(DOCTOR, "DOCTOR");

    @AfterEach
    void clearTransactionMarker() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void lookup_parsesTheLockedProducerFixtureAndSignsVerifiedActorClaims() throws Exception {
        when(client.lookup(eq(ORDER), anyString(), eq(CORRELATION))).thenReturn(response(fixture()));

        var context = adapter.findById(ORDER, CORRELATION, actor).orElseThrow();

        assertThat(context.sourceOrderId()).isEqualTo(ORDER);
        assertThat(context.recordId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000001002"));
        assertThat(context.patientId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000001003"));
        assertThat(context.requestingDoctorId()).isEqualTo(DOCTOR);
        assertThat(context.requestingDepartmentId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000001005"));
        assertThat(context.careEpisodeType().name()).isEqualTo("OUTPATIENT_VISIT");
        assertThat(context.careEpisodeId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000001006"));
        assertThat(context.labType()).isEqualTo("CBC");
        assertThat(context.requestedDate()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(context.priceCode()).isEqualTo("LAB-CBC");

        var authorization = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client).lookup(eq(ORDER), authorization.capture(), eq(CORRELATION));
        assertThat(authorization.getValue()).startsWith("Bearer ");
        var signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().verifyWith(signingKey).clock(() -> Date.from(NOW)).build()
                .parseSignedClaims(authorization.getValue().substring("Bearer ".length())).getPayload();
        assertThat(claims.getSubject()).isEqualTo("lab-service");
        assertThat(claims.get(JwtClaims.ROLE, String.class)).isEqualTo("SYSTEM");
        assertThat(claims.get(JwtClaims.TYPE, String.class)).isEqualTo("service");
        assertThat(claims.get(JwtClaims.STAFF_ID, String.class)).isEqualTo(DOCTOR.toString());
        assertThat(claims.get("originalActorRole", String.class)).isEqualTo("DOCTOR");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(60_000L);
    }

    @Test
    void lookup_distinguishesConfirmedAbsenceFromUnavailableAuthority() throws Exception {
        when(client.lookup(eq(ORDER), anyString(), eq(CORRELATION))).thenReturn(response(absentFixture()));
        assertThat(adapter.findById(ORDER, CORRELATION, actor)).isEmpty();

        when(client.lookup(eq(ORDER), anyString(), eq(CORRELATION))).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> adapter.findById(ORDER, CORRELATION, actor))
                .isInstanceOf(LabUpstreamUnavailableException.class);
    }

    @Test
    void lookup_failsClosedForMalformedOrMismatchedResponses() throws Exception {
        when(client.lookup(eq(ORDER), anyString(), eq(CORRELATION)))
                .thenReturn(response(fixture() + " {}"));
        assertThatThrownBy(() -> adapter.findById(ORDER, CORRELATION, actor))
                .isInstanceOf(LabUpstreamUnavailableException.class);

        when(client.lookup(eq(ORDER), anyString(), eq(CORRELATION)))
                .thenReturn(response(fixture().replace("LAB-CBC", " ")));
        assertThatThrownBy(() -> adapter.findById(ORDER, CORRELATION, actor))
                .isInstanceOf(LabUpstreamUnavailableException.class);

        when(client.lookup(eq(ORDER), anyString(), eq(CORRELATION)))
                .thenReturn(ResponseEntity.ok().header("X-Correlation-Id", "wrong").body(fixture()));
        assertThatThrownBy(() -> adapter.findById(ORDER, CORRELATION, actor))
                .isInstanceOf(LabUpstreamUnavailableException.class);
    }

    @Test
    void lookup_mapsClinicalActorDenialToForbiddenAndCircuitOpenToUnavailable() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/lab-orders/" + ORDER + "/context",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response forbiddenResponse = Response.builder().request(request).status(403)
                .reason("Forbidden").headers(Map.of()).build();
        when(client.lookup(eq(ORDER), anyString(), eq(CORRELATION)))
                .thenThrow(FeignException.errorStatus("clinical", forbiddenResponse));
        assertThatThrownBy(() -> adapter.findById(ORDER, CORRELATION, actor))
                .isInstanceOf(ForbiddenOperationException.class);

        org.mockito.Mockito.clearInvocations(client);
        CircuitBreaker open = CircuitBreaker.of("clinical-lab-order-open", CircuitBreakerConfig.ofDefaults());
        open.transitionToOpenState();
        var openAdapter = adapter(open);
        assertThatThrownBy(() -> openAdapter.findById(ORDER, CORRELATION, actor))
                .isInstanceOf(LabUpstreamUnavailableException.class);
        verify(client, never()).lookup(eq(ORDER), anyString(), eq(CORRELATION));
    }

    @Test
    void lookup_neverConfusesHttpErrorsWithConfirmedAbsence() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/lab-orders/" + ORDER + "/context",
                Map.of(), null, StandardCharsets.UTF_8, null);
        for (int status : new int[]{401, 404, 500, 503}) {
            Response errorResponse = Response.builder().request(request).status(status)
                    .reason("upstream error").headers(Map.of()).build();
            when(client.lookup(eq(ORDER), anyString(), eq(CORRELATION)))
                    .thenThrow(FeignException.errorStatus("clinical", errorResponse));
            assertThatThrownBy(() -> adapter.findById(ORDER, CORRELATION, actor))
                    .isInstanceOf(LabUpstreamUnavailableException.class);
        }
    }

    @Test
    void lookup_requiresActorAndRunsOutsideWriteTransaction() {
        assertThatThrownBy(() -> adapter.findById(ORDER, CORRELATION, null))
                .isInstanceOf(IllegalArgumentException.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> adapter.findById(ORDER, CORRELATION, actor))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(client);
    }

    private LabClinicalOrderContextAdapter adapter(CircuitBreaker circuitBreaker) {
        return new LabClinicalOrderContextAdapter(client, clock,
                new com.mediflow.lab.infrastructure.security.JwtProperties(SECRET), mapper, circuitBreaker);
    }

    private ResponseEntity<String> response(String body) {
        return ResponseEntity.ok().header(JwtClaims.HEADER_CORRELATION_ID, CORRELATION).body(body);
    }

    private String fixture() throws IOException {
        try (var stream = getClass().getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IOException("Missing producer fixture");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private static String absentFixture() {
        return "{\"success\":true,\"data\":{\"exists\":false,\"sourceOrderId\":\"" + ORDER
                + "\",\"recordId\":null,\"patientId\":null,\"requestingDoctorId\":null,"
                + "\"requestingDepartmentId\":null,\"careEpisodeType\":null,\"careEpisodeId\":null,"
                + "\"labType\":null,\"requestedDate\":null,\"priceCode\":null,\"orderedAt\":null,"
                + "\"observedAt\":\"2026-09-21T10:00:00Z\"},\"error\":null,"
                + "\"timestamp\":\"2026-09-21T10:00:00Z\",\"correlationId\":\"" + CORRELATION + "\"}";
    }
}
