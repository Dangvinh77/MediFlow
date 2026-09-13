package com.mediflow.clinical.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.mediflow.clinical.application.exception.UpstreamUnavailableException;

import feign.FeignException;

class PatientLookupAdapterTest {

    private final PatientFeignClient client = mock(PatientFeignClient.class);
    private final PatientLookupAdapter adapter = new PatientLookupAdapter(client);

    @Test
    void exists_successfulLookup_returnsTrue() {
        UUID patientId = UUID.randomUUID();
        when(client.findById(patientId)).thenReturn(ResponseEntity.ok().build());
        assertThat(adapter.exists(patientId)).isTrue();
    }

    @Test
    void exists_confirmedNotFound_returnsFalse() {
        UUID patientId = UUID.randomUUID();
        when(client.findById(patientId)).thenThrow(mock(FeignException.NotFound.class));
        assertThat(adapter.exists(patientId)).isFalse();
    }

    @Test
    void exists_transportFailure_throwsTypedUnavailable() {
        UUID patientId = UUID.randomUUID();
        when(client.findById(patientId)).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> adapter.exists(patientId))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessageContaining("patient-service");
    }
}
