package com.mediflow.lab.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.lab.application.exception.LabUpstreamUnavailableException;
import com.mediflow.lab.application.port.out.ClinicalReadDecisionPort.ClinicalReadDecision;
import com.mediflow.lab.application.port.out.ReaderIdentity;
import com.mediflow.lab.infrastructure.client.LabClinicalReadDecisionFeignClient.ReadDecisionBody;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

class LabClinicalReadDecisionAdapterTest {
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    private static final String SECRET = "lab-read-decision-test-secret-at-least-32-bytes";
    private static final UUID PATIENT = UUID.randomUUID();
    private static final ReaderIdentity READER = new ReaderIdentity(UUID.randomUUID(), UUID.randomUUID(), "DOCTOR", null);

    private final LabClinicalReadDecisionFeignClient client = mock(LabClinicalReadDecisionFeignClient.class);
    private final LabClinicalReadDecisionAdapter adapter = new LabClinicalReadDecisionAdapter(client,
            Clock.fixed(NOW, ZoneOffset.UTC), SECRET, CircuitBreaker.ofDefaults("test"));

    @Test
    void returnsClinicalDecisionForTheRequestedPatient() {
        respond(new ReadDecisionBody(PATIENT, false, null, "RECORD_ACCESS_PURPOSE_REQUIRED", NOW));

        assertThat(adapter.decide(PATIENT, READER, "corr-1"))
                .isEqualTo(new ClinicalReadDecision(false, "RECORD_ACCESS_PURPOSE_REQUIRED"));
    }

    @Test
    void answerForAnotherPatientFailsClosed() {
        respond(new ReadDecisionBody(UUID.randomUUID(), true, "AUTHOR", null, NOW));

        assertThatThrownBy(() -> adapter.decide(PATIENT, READER, "corr-1"))
                .isInstanceOf(LabUpstreamUnavailableException.class);
    }

    @Test
    void transportFailureFailsClosed() {
        when(client.decide(eq(PATIENT), any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> adapter.decide(PATIENT, READER, "corr-1"))
                .isInstanceOf(LabUpstreamUnavailableException.class);
    }

    private void respond(ReadDecisionBody body) {
        when(client.decide(eq(PATIENT), eq(READER.accountId()), eq(READER.staffId()), eq("DOCTOR"), any(), eq("corr-1")))
                .thenReturn(ApiResponse.ok(body, "corr-1"));
    }
}
