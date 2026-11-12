package com.mediflow.clinical.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.clinical.infrastructure.client.ClinicalRecordReviewAuthorityFeignClient.RecordReviewAuthority;

class ClinicalRecordReviewAuthorityAdapterTest {
    private final ClinicalRecordReviewAuthorityFeignClient client = mock(ClinicalRecordReviewAuthorityFeignClient.class);
    private final ClinicalRecordReviewAuthorityAdapter adapter = new ClinicalRecordReviewAuthorityAdapter(client);
    private final UUID accountId = UUID.randomUUID();
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void resetClient() {
        org.mockito.Mockito.reset(client);
    }

    @Test
    void returnsRecordReviewAuthorityFromCore() {
        when(client.authority(accountId, correlationId.toString())).thenReturn(ApiResponse.ok(
                new RecordReviewAuthority(accountId, UUID.randomUUID(), true), correlationId.toString()));

        assertThat(adapter.canReviewRecords(accountId, correlationId)).isTrue();
        verify(client).authority(accountId, correlationId.toString());
    }

    @Test
    void returnsFalseForConfirmedNoAuthority() {
        when(client.authority(accountId, correlationId.toString())).thenReturn(ApiResponse.ok(
                new RecordReviewAuthority(accountId, UUID.randomUUID(), false), correlationId.toString()));

        assertThat(adapter.canReviewRecords(accountId, correlationId)).isFalse();
    }

    @Test
    void timeoutIsNotTreatedAsNoAuthority() {
        when(client.authority(accountId, correlationId.toString())).thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> adapter.canReviewRecords(accountId, correlationId))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void nullEnvelopeFailsAsUnavailable() {
        when(client.authority(accountId, correlationId.toString())).thenReturn(null);

        assertThatThrownBy(() -> adapter.canReviewRecords(accountId, correlationId))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void failedEnvelopeFailsAsUnavailable() {
        when(client.authority(accountId, correlationId.toString())).thenReturn(
                ApiResponse.fail(ApiResponse.ApiError.of("CORE_ERROR", "unavailable"), correlationId.toString()));

        assertThatThrownBy(() -> adapter.canReviewRecords(accountId, correlationId))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void nullPayloadFailsAsUnavailable() {
        when(client.authority(accountId, correlationId.toString())).thenReturn(
                new ApiResponse<>(true, null, null, java.time.Instant.now(), correlationId.toString()));

        assertThatThrownBy(() -> adapter.canReviewRecords(accountId, correlationId))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void missingAuthorityFlagFailsAsUnavailable() {
        when(client.authority(accountId, correlationId.toString())).thenReturn(ApiResponse.ok(
                new RecordReviewAuthority(accountId, UUID.randomUUID(), null), correlationId.toString()));

        assertThatThrownBy(() -> adapter.canReviewRecords(accountId, correlationId))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void mismatchedActorFailsAsUnavailable() {
        when(client.authority(accountId, correlationId.toString())).thenReturn(ApiResponse.ok(
                new RecordReviewAuthority(UUID.randomUUID(), UUID.randomUUID(), true), correlationId.toString()));

        assertThatThrownBy(() -> adapter.canReviewRecords(accountId, correlationId))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void missingStaffForPositiveAuthorityFailsAsUnavailable() {
        when(client.authority(accountId, correlationId.toString())).thenReturn(ApiResponse.ok(
                new RecordReviewAuthority(accountId, null, true), correlationId.toString()));

        assertThatThrownBy(() -> adapter.canReviewRecords(accountId, correlationId))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void mismatchedCorrelationFailsAsUnavailable() {
        when(client.authority(accountId, correlationId.toString())).thenReturn(
                new ApiResponse<>(true, new RecordReviewAuthority(accountId, UUID.randomUUID(), false), null,
                        java.time.Instant.now(), "wrong-correlation"));

        assertThatThrownBy(() -> adapter.canReviewRecords(accountId, correlationId))
                .isInstanceOf(UpstreamUnavailableException.class);
    }
}
