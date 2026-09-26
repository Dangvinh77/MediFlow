package com.mediflow.clinical.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class PatientLookupResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
