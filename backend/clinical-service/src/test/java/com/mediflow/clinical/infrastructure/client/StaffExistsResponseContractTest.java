package com.mediflow.clinical.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class StaffExistsResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void organizationPayload_preservesDoctorEligibilityAndIgnoresAdditiveFields() throws Exception {
        StaffExistsResponse response = objectMapper.readValue("""
                {
                  "exists": true,
                  "eligibleDoctor": true,
                  "departmentId": "2f4d8d4a-8c2c-4f5d-8ed0-bcf9159e5bd1",
                  "futureField": "ignored"
                }
                """, StaffExistsResponse.class);

        JsonNode serialized = objectMapper.valueToTree(response);
        assertThat(serialized.path("eligibleDoctor").asBoolean()).isTrue();
        assertThat(serialized.path("departmentId").asText())
                .isEqualTo("2f4d8d4a-8c2c-4f5d-8ed0-bcf9159e5bd1");
    }
}
