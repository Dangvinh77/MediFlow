package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.application.event.AdmissionRequestedPayload;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.domain.model.AdmissionPriority;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalAdmissionRequestedContractFixtureTest {

    private static final String FIXTURE = "/contracts/admission.requested.v1.json";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializesVersionOneAdmissionRequestAsCanonicalFixture() throws IOException {
        Instant requestedAt = Instant.parse("2026-09-28T02:00:00Z");
        var payload = new AdmissionRequestedPayload(
                id(2), id(3), id(4), id(5), id(6), "Community-acquired pneumonia",
                AdmissionPriority.URGENT, false, requestedAt);
        DomainEventEnvelope<AdmissionRequestedPayload> event = new DomainEventEnvelope<>(
                id(1), "admission.requested", 1, requestedAt, id(7).toString(), "clinical-service", payload);

        String serialized = objectMapper.writeValueAsString(event);
        JsonNode fixture = objectMapper.readTree(readFixture());

        assertThat(objectMapper.readTree(serialized)).isEqualTo(fixture);
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }

    private byte[] readFixture() throws IOException {
        try (var input = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(input).as("canonical admission.requested fixture").isNotNull();
            return input.readAllBytes();
        }
    }
}
