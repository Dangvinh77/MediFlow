package com.mediflow.clinical.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.common.api.ApiResponse;

class PatientLookupResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void patientProducerFixture_decodesCanonicalEnvelopeAndIdentity() throws Exception {
        try (InputStream fixture = getClass().getResourceAsStream("/contracts/patient.lookup.exists.json")) {
            assertThat(fixture).isNotNull();

            ApiResponse<PatientLookupResponse> response = objectMapper.readValue(fixture,
                    new TypeReference<>() { });

            assertThat(response.success()).isTrue();
            assertThat(response.error()).isNull();
            assertThat(response.correlationId())
                    .isEqualTo("10000000-0000-4000-8000-000000000001");
            assertThat(response.data().exists()).isTrue();
            assertThat(response.data().patientId().toString())
                    .isEqualTo("00000000-0000-4000-8000-000000000001");
        }
    }

    @Test
    void patientPayload_ignoresAdditiveFieldsAndKeepsCanonicalIdentity() throws Exception {
        PatientLookupResponse response = objectMapper.readValue("""
                {
                  "exists": true,
                  "patientId": "550e8400-e29b-41d4-a716-446655440000",
                  "futureField": "ignored"
                }
                """, PatientLookupResponse.class);

        assertThat(response.exists()).isTrue();
        assertThat(response.patientId().toString())
                .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }
}
