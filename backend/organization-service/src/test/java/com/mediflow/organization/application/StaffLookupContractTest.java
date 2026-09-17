package com.mediflow.organization.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.dto.response.StaffLookupDTO;

class StaffLookupContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void response_serializesEnvelopeAndRequiredFields() throws Exception {
        UUID departmentId = UUID.randomUUID();
        String json = objectMapper.writeValueAsString(ApiResponse.ok(
                StaffLookupDTO.eligible(departmentId),
                "correlation-1"));

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.path("success").asBoolean()).isTrue();
        assertThat(node.path("data").path("exists").asBoolean()).isTrue();
        assertThat(node.path("data").path("eligibleDoctor").asBoolean()).isTrue();
        assertThat(node.path("data").path("departmentId").asText())
                .isEqualTo(departmentId.toString());
        assertThat(node.path("correlationId").asText()).isEqualTo("correlation-1");
    }

    @Test
    void response_deserializesWhenConsumerAddsUnknownField() throws Exception {
        UUID departmentId = UUID.randomUUID();
        String json = """
                {
                  "success": true,
                  "data": {
                    "exists": true,
                    "eligibleDoctor": true,
                    "departmentId": "%s",
                    "futureField": "additive"
                  },
                  "error": null,
                  "timestamp": "2026-09-18T00:00:00Z",
                  "correlationId": "correlation-1"
                }
                """.formatted(departmentId);

        ApiResponse<StaffLookupDTO> response = objectMapper.readValue(
                json,
                new TypeReference<ApiResponse<StaffLookupDTO>>() {});

        assertThat(response.data()).isEqualTo(
                StaffLookupDTO.eligible(departmentId));
    }
}
