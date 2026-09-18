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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.common.api.ApiResponse;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;

class StaffLookupAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final OrganizationFeignClient client = mock(OrganizationFeignClient.class);
    private final StaffLookupAdapter adapter = new StaffLookupAdapter(client);

    @Test
    void departmentOf_existingStaff_returnsDepartment() {
        UUID staffId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(client.exists(staffId)).thenReturn(ApiResponse.ok(new StaffExistsResponse(true, true, departmentId)));
        assertThat(adapter.departmentOf(staffId)).contains(departmentId);
    }

    @Test
    void departmentOf_confirmedMissingStaff_returnsEmpty() {
        UUID staffId = UUID.randomUUID();
        when(client.exists(staffId)).thenReturn(ApiResponse.ok(new StaffExistsResponse(false, false, null)));
        assertThat(adapter.departmentOf(staffId)).isEmpty();
    }

    @Test
    void departmentOf_existingIneligibleStaff_returnsEmpty() throws Exception {
        UUID staffId = UUID.randomUUID();
        StaffExistsResponse response = JSON.readValue("""
                {"exists":true,"eligibleDoctor":false,"departmentId":null}
                """, StaffExistsResponse.class);
        when(client.exists(staffId)).thenReturn(ApiResponse.ok(response));

        assertThat(adapter.departmentOf(staffId)).isEmpty();
    }

    @Test
    void departmentOf_directNotFound_returnsEmpty() {
        UUID staffId = UUID.randomUUID();
        when(client.exists(staffId)).thenThrow(notFound());
        assertThat(adapter.departmentOf(staffId)).isEmpty();
    }

    @Test
    void departmentOf_circuitBreakerWrappedNotFound_returnsEmpty() {
        UUID staffId = UUID.randomUUID();
        when(client.exists(staffId)).thenThrow(
                new NoFallbackAvailableException("no fallback", notFound()));
        assertThat(adapter.departmentOf(staffId)).isEmpty();
    }

    @Test
    void departmentOf_invalidEnvelope_throwsTypedUnavailable() {
        UUID staffId = UUID.randomUUID();
        when(client.exists(staffId)).thenReturn(null);
        assertThatThrownBy(() -> adapter.departmentOf(staffId))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("invalid response");
    }

    @Test
    void departmentOf_existingStaffWithoutDepartment_throwsTypedUnavailable() {
        UUID staffId = UUID.randomUUID();
        when(client.exists(staffId)).thenReturn(ApiResponse.ok(new StaffExistsResponse(true, true, null)));
        assertThatThrownBy(() -> adapter.departmentOf(staffId))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("invalid response");
    }

    @Test
    void departmentOf_circuitBreakerWrappedTimeout_throwsTypedUnavailable() {
        UUID staffId = UUID.randomUUID();
        RetryableException timeout = new RetryableException(0, "timeout", Request.HttpMethod.GET,
                new SocketTimeoutException("read timed out"), (Long) null, request());
        when(client.exists(staffId)).thenThrow(new NoFallbackAvailableException("no fallback", timeout));
        assertUnavailable(staffId);
    }

    @Test
    void departmentOf_circuitBreakerWrappedConnectionFailure_throwsTypedUnavailable() {
        UUID staffId = UUID.randomUUID();
        RetryableException connectionFailure = new RetryableException(0, "connection refused", Request.HttpMethod.GET,
                new ConnectException("connection refused"), (Long) null, request());
        when(client.exists(staffId)).thenThrow(
                new NoFallbackAvailableException("no fallback", connectionFailure));
        assertUnavailable(staffId);
    }

    @Test
    void departmentOf_circuitBreakerWrappedServerFailure_throwsTypedUnavailable() {
        UUID staffId = UUID.randomUUID();
        when(client.exists(staffId)).thenThrow(
                new NoFallbackAvailableException("no fallback", serverError()));
        assertUnavailable(staffId);
    }

    @Test
    void departmentOf_circuitBreakerWrappedOpenCircuit_throwsTypedUnavailable() {
        UUID staffId = UUID.randomUUID();
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("organization-service");
        circuitBreaker.transitionToOpenState();
        CallNotPermittedException openCircuit =
                CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
        when(client.exists(staffId)).thenThrow(new NoFallbackAvailableException("no fallback", openCircuit));
        assertUnavailable(staffId);
    }

    @Test
    void departmentOf_cyclicCause_throwsTypedUnavailableWithoutLooping() {
        UUID staffId = UUID.randomUUID();
        when(client.exists(staffId)).thenThrow(new CyclicCauseException());
        assertUnavailable(staffId);
    }

    private void assertUnavailable(UUID staffId) {
        assertThatThrownBy(() -> adapter.departmentOf(staffId))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("organization-service");
    }

    private static FeignException.NotFound notFound() {
        return new FeignException.NotFound("staff not found", request(), new byte[0], Collections.emptyMap());
    }

    private static FeignException.InternalServerError serverError() {
        return new FeignException.InternalServerError(
                "organization service failed", request(), new byte[0], Collections.emptyMap());
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "http://organization-service/api/v1/org/staff/test",
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
