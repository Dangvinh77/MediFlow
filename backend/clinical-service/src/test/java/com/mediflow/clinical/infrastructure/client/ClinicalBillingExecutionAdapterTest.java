package com.mediflow.clinical.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.command.FinancialExecutionStartRequest;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ClinicalBillingExecutionAdapterTest {
    private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");
    private static final String CORRELATION = "8fd90df3-1e7c-4f86-88f6-539ab69e9e80";
    private static final UUID CLEARANCE = UUID.fromString("00000000-0000-0000-0000-000000000052");
    private static final UUID APPOINTMENT = UUID.fromString("00000000-0000-0000-0000-000000000053");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-000000000054");

    private final ClinicalBillingExecutionFeignClient client = mock(ClinicalBillingExecutionFeignClient.class);
    private final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("clinical-billing-test");
    private final ClinicalBillingExecutionAdapter adapter = new ClinicalBillingExecutionAdapter(client,
            new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC), circuitBreaker);

    @Test
    void startPostsStableAppointmentOperationAndAcceptsExactBillingReceipt() {
        var request = request();
        when(client.start(any(), any(), any())).thenReturn(response(CORRELATION, """
                {"operationId":"%s","clearanceId":"%s","sourceType":"EXAM","sourceId":"%s","state":"STARTED","startedAt":"%s"}
                """.formatted(APPOINTMENT, CLEARANCE, APPOINTMENT, NOW)));

        adapter.start(CLEARANCE, request, CORRELATION);

        verify(client).start(org.mockito.ArgumentMatchers.eq(CLEARANCE), org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq(CORRELATION));
    }

    @Test
    void startAcceptsOriginalReceiptTimestampOnAnIdempotentRetry() {
        var request = request();
        Instant originalStart = NOW.minusSeconds(600);
        when(client.start(any(), any(), any())).thenReturn(response(CORRELATION, """
                {"operationId":"%s","clearanceId":"%s","sourceType":"EXAM","sourceId":"%s","state":"STARTED","startedAt":"%s"}
                """.formatted(APPOINTMENT, CLEARANCE, APPOINTMENT, originalStart)));

        adapter.start(CLEARANCE, request, CORRELATION);
    }

    @Test
    void startRejectsReceiptForDifferentAppointment() {
        UUID other = UUID.randomUUID();
        when(client.start(any(), any(), any())).thenReturn(response(CORRELATION, """
                {"operationId":"%s","clearanceId":"%s","sourceType":"EXAM","sourceId":"%s","state":"STARTED","startedAt":"%s"}
                """.formatted(other, CLEARANCE, other, NOW)));

        assertThatThrownBy(() -> adapter.start(CLEARANCE, request(), CORRELATION))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void startMapsBillingConflictToFinancialAuthorizationDenial() {
        Request request = Request.create(Request.HttpMethod.POST, "http://billing.invalid", Collections.emptyMap(),
                null, StandardCharsets.UTF_8, null);
        Response conflict = Response.builder().status(409).reason("Conflict").request(request)
                .headers(Map.of()).body("{}".getBytes(StandardCharsets.UTF_8)).build();
        when(client.start(any(), any(), any())).thenThrow(FeignException.errorStatus("execution-start", conflict));

        assertThatThrownBy(() -> adapter.start(CLEARANCE, request(), CORRELATION))
                .isInstanceOf(InvalidClinicalDataException.class)
                .hasFieldOrPropertyWithValue("code", "CLINICAL_CLEARANCE_REQUIRED");
    }

    @Test
    void startFailsClosedWhenBillingCannotBeReached() {
        when(client.start(any(), any(), any())).thenThrow(new IllegalStateException("network failure"));

        assertThatThrownBy(() -> adapter.start(CLEARANCE, request(), CORRELATION))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void startFailsClosedOnMalformedBillingReceipt() {
        when(client.start(any(), any(), any())).thenReturn(response(CORRELATION, "{}"));

        assertThatThrownBy(() -> adapter.start(CLEARANCE, request(), CORRELATION))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    private static FinancialExecutionStartRequest request() {
        return new FinancialExecutionStartRequest(APPOINTMENT, "EXAM", APPOINTMENT, PATIENT,
                "OUTPATIENT_VISIT", APPOINTMENT);
    }

    private static ResponseEntity<String> response(String correlation, String data) {
        return ResponseEntity.ok().header("X-Correlation-Id", correlation).body("""
                {"success":true,"data":%s,"error":null,"correlationId":"%s"}
                """.formatted(data.strip(), correlation));
    }
}
