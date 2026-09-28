package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.MedicalRecordCompletedPayload;
import com.mediflow.clinical.domain.model.RecordDisposition;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalMedicalRecordCompletedContractFixtureTest {

    private static final String FIXTURE = "/contracts/medicalrecord.completed.v1.json";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializesVersionOneCompletedRecordAsCanonicalFixture() throws IOException {
        Instant completedAt = Instant.parse("2026-09-28T03:00:00Z");
        var payload = new MedicalRecordCompletedPayload(
                id(3), id(2), id(4), id(5), RecordDisposition.ADMISSION, true, completedAt);
        var event = new DomainEventEnvelope<>(id(11), "medicalrecord.completed", 1,
                completedAt, id(7).toString(), "clinical-service", payload);

        JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsBytes(event));
        JsonNode fixture = objectMapper.readTree(readFixture());

        assertThat(actual).isEqualTo(fixture);
    }

    private byte[] readFixture() throws IOException {
        try (var input = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(input).as("canonical medicalrecord.completed fixture").isNotNull();
            return input.readAllBytes();
        }
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
