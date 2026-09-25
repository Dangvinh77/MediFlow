package com.mediflow.clinical.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;

import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.common.api.ApiResponse;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

class PatientLookupAdapterTest {

    private final PatientFeignClient client = mock(PatientFeignClient.class);
    private final PatientLookupAdapter adapter = new PatientLookupAdapter(client);

    @Test
    void exists_confirmedExistingPatient_returnsTrue() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId))
                .thenReturn(ApiResponse.ok(new PatientLookupResponse(true, patientId)));

        assertThat(adapter.exists(patientId)).isTrue();
    }

    @Test
    void exists_confirmedMissingPatient_returnsFalse() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId))
                .thenReturn(ApiResponse.ok(new PatientLookupResponse(false, patientId)));

        assertThat(adapter.exists(patientId)).isFalse();
    }

    @Test
    void exists_directNotFound_returnsFalse() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId)).thenThrow(notFound());

        assertThat(adapter.exists(patientId)).isFalse();
    }

    @Test
    void exists_circuitBreakerWrappedNotFound_returnsFalse() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId)).thenThrow(
                new NoFallbackAvailableException("no fallback", notFound()));

        assertThat(adapter.exists(patientId)).isFalse();
    }

    @Test
    void exists_nullEnvelope_throwsTypedUnavailable() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId)).thenReturn(null);

        assertInvalidResponse(patientId);
    }

    @Test
    void exists_failedEnvelope_throwsTypedUnavailable() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId)).thenReturn(ApiResponse.fail(
                ApiResponse.ApiError.of("UPSTREAM_ERROR", "lookup failed")));

        assertInvalidResponse(patientId);
    }

    @Test
    void exists_missingPayload_throwsTypedUnavailable() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId)).thenReturn(ApiResponse.ok(null));

        assertInvalidResponse(patientId);
    }

    @Test
    void exists_missingCanonicalPatientId_throwsTypedUnavailable() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId))
                .thenReturn(ApiResponse.ok(new PatientLookupResponse(true, null)));

        assertInvalidResponse(patientId);
    }

    @Test
    void exists_mismatchedCanonicalPatientId_throwsTypedUnavailable() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId))
                .thenReturn(ApiResponse.ok(new PatientLookupResponse(true, UUID.randomUUID())));

        assertInvalidResponse(patientId);
    }

    @Test
    void exists_transportFailure_throwsTypedUnavailable() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId)).thenThrow(new IllegalStateException("timeout"));

        assertUnavailable(patientId);
    }

    @Test
    void exists_circuitBreakerWrappedTimeout_throwsTypedUnavailable() {
        UUID patientId = UUID.randomUUID();
        RetryableException timeout = new RetryableException(0, "timeout", Request.HttpMethod.GET,
                new SocketTimeoutException("read timed out"), (Long) null, request());
        when(client.exists(patientId)).thenThrow(new NoFallbackAvailableException("no fallback", timeout));

        assertUnavailable(patientId);
    }

    @Test
    void exists_circuitBreakerWrappedConnectionFailure_throwsTypedUnavailable() {
        UUID patientId = UUID.randomUUID();
        RetryableException connectionFailure = new RetryableException(0, "connection refused", Request.HttpMethod.GET,
                new ConnectException("connection refused"), (Long) null, request());
        when(client.exists(patientId)).thenThrow(
                new NoFallbackAvailableException("no fallback", connectionFailure));

        assertUnavailable(patientId);
    }

    @Test
    void exists_circuitBreakerWrappedServerFailure_throwsTypedUnavailable() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId)).thenThrow(
                new NoFallbackAvailableException("no fallback", serverError()));

        assertUnavailable(patientId);
    }

    @Test
    void exists_circuitBreakerWrappedOpenCircuit_throwsTypedUnavailable() {
        UUID patientId = UUID.randomUUID();
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("patient-service");
        circuitBreaker.transitionToOpenState();
        CallNotPermittedException openCircuit =
                CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
        when(client.exists(patientId)).thenThrow(new NoFallbackAvailableException("no fallback", openCircuit));

        assertUnavailable(patientId);
    }

    @Test
    void exists_cyclicCause_throwsTypedUnavailableWithoutLooping() {
        UUID patientId = UUID.randomUUID();
        when(client.exists(patientId)).thenThrow(new CyclicCauseException());

        assertUnavailable(patientId);
    }

    private void assertInvalidResponse(UUID patientId) {
        assertThatThrownBy(() -> adapter.exists(patientId))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("invalid response");
    }

    private void assertUnavailable(UUID patientId) {
        assertThatThrownBy(() -> adapter.exists(patientId))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("patient-service");
    }

    private static FeignException.NotFound notFound() {
        return new FeignException.NotFound("patient not found", request(), new byte[0], Collections.emptyMap());
    }

    private static FeignException.InternalServerError serverError() {
        return new FeignException.InternalServerError(
                "patient service failed", request(), new byte[0], Collections.emptyMap());
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "http://patient-service/api/v1/patients/test/exists",
                Collections.emptyMap(), null, StandardCharsets.UTF_8);
    }

    private static final class CyclicCauseException extends RuntimeException {
        private CyclicCauseException() {
            super(null, null, false, false);
        }

        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }
}
