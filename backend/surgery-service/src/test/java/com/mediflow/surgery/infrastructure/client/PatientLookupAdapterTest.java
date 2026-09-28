package com.mediflow.surgery.infrastructure.client;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientLookupAdapterTest {

    private final PatientFeignClient client = mock(PatientFeignClient.class);
    private final ServiceTokenFactory tokens = mock(ServiceTokenFactory.class);
    private final PatientLookupAdapter adapter = new PatientLookupAdapter(client, tokens);
    private final UUID patientId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @Test
    void exists_matchingConfirmedResponse_returnsAuthorityValueAndPropagatesHeaders() {
        when(tokens.bearerToken()).thenReturn("Bearer service-token");
        when(client.exists(patientId, "Bearer service-token", correlationId.toString()))
                .thenReturn(ApiResponse.ok(new PatientLookupResponse(true, patientId)));

        assertThat(adapter.exists(patientId, correlationId)).isTrue();
        verify(client).exists(patientId, "Bearer service-token", correlationId.toString());
    }

    @Test
    void exists_confirmedFalseAndHttp404_areAbsence() {
        when(client.exists(patientId, null, correlationId.toString()))
                .thenReturn(ApiResponse.ok(new PatientLookupResponse(false, patientId)))
                .thenThrow(new FeignException.NotFound("missing", request(), new byte[0], Collections.emptyMap()));

        assertThat(adapter.exists(patientId, correlationId)).isFalse();
        assertThat(adapter.exists(patientId, correlationId)).isFalse();
    }

    @Test
    void exists_malformedOrMismatchedResponse_isUnavailable() {
        when(client.exists(patientId, null, correlationId.toString()))
                .thenReturn(ApiResponse.ok(new PatientLookupResponse(null, patientId)))
                .thenReturn(ApiResponse.ok(new PatientLookupResponse(true, UUID.randomUUID())))
                .thenReturn(ApiResponse.fail(ApiResponse.ApiError.of("UPSTREAM", "failed")));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> adapter.exists(patientId, correlationId))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }
    }

    @Test
    void exists_serverFailureAndTimeout_areUnavailable() {
        when(client.exists(patientId, null, correlationId.toString()))
                .thenThrow(new FeignException.InternalServerError("failed", request(), new byte[0], Collections.emptyMap()))
                .thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> adapter.exists(patientId, correlationId))
                .isInstanceOf(UpstreamUnavailableException.class);
        assertThatThrownBy(() -> adapter.exists(patientId, correlationId))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void exists_missingCommandIdentity_isRejectedBeforeNetwork() {
        assertThatThrownBy(() -> adapter.exists(null, correlationId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.exists(patientId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Request request() {
        return Request.create(Request.HttpMethod.GET,
                "http://patient-service/api/v1/patients/" + patientId + "/exists",
                Collections.emptyMap(), null, StandardCharsets.UTF_8);
    }
}
