package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.MedicalRecordCreatedV2Payload;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalMedicalRecordCreatedContractFixtureTest {

    private static final String FIXTURE = "/contracts/medicalrecord.created.v1.json";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void recordCreatedMatchesCanonicalExamSourceFixture() throws IOException {
        Instant occurredAt = Instant.parse("2026-10-10T09:30:00Z");
        var payload = new MedicalRecordCreatedV2Payload(
                id(8), id(2), id(3), id(5), id(4),
                CareEpisodeType.OUTPATIENT_VISIT, id(2),
                "EXAM", id(2), "OUTPATIENT_EXAM", LocalDate.parse("2026-10-10"));
        var event = new DomainEventEnvelope<>(id(9), "medicalrecord.created", 1,
                occurredAt, id(7).toString(), "clinical-service", payload);

        JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsBytes(event));
        JsonNode fixture = objectMapper.readTree(readFixture());

        assertThat(actual).isEqualTo(fixture);
        assertThat(fixture.at("/payload/sourceId").asText())
                .isEqualTo(fixture.at("/payload/appointmentId").asText());
        assertThat(fixture.at("/payload/careEpisodeId").asText())
                .isEqualTo(fixture.at("/payload/appointmentId").asText());
    }

    private byte[] readFixture() throws IOException {
        try (var input = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(input).as("canonical medicalrecord.created fixture").isNotNull();
            return input.readAllBytes();
        }
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
