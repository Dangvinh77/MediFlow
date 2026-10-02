package com.mediflow.lab.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.lab.domain.model.CareEpisodeType;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabCareFinanceV2ContractFixtureTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-28T06:00:00Z");
    private static final String CORRELATION_ID = id(7).toString();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void labRequestCreatedMatchesCanonicalV1Fixture() throws IOException {
        var payload = new LabRequestV2Payload(id(2), id(3), id(4), id(5),
                CareEpisodeType.OUTPATIENT_VISIT, id(6), id(8), "LAB_TEST", id(2),
                "LAB-CBC", "CBC", OCCURRED_AT, null);
        var event = new DomainEventEnvelope<>(id(1), "lab.request.created", 1,
                OCCURRED_AT, CORRELATION_ID, "lab-service", payload);

        assertMatchesFixture("lab.request.created.v1.json", event);
    }

    @Test
    void labResultCreatedMatchesCanonicalV1Fixture() throws IOException {
        var result = new LabResultCreatedEvent.Result(id(12), "Hemoglobin", "13.5", "g/dL", "12-16");
        var payload = new LabResultV2Payload(id(2), id(3), id(4), id(5),
                CareEpisodeType.OUTPATIENT_VISIT, id(6), "CBC", 1, List.of(result),
                "Normal", id(9), LocalDate.parse("2026-09-28"), OCCURRED_AT);
        var event = new DomainEventEnvelope<>(id(11), "lab.result.created", 1,
                OCCURRED_AT, CORRELATION_ID, "lab-service", payload);

        assertMatchesFixture("lab.result.created.v1.json", event);
    }

    @Test
    void admissionLabResultCreatedMatchesCanonicalV1Fixture() throws IOException {
        var result = new LabResultCreatedEvent.Result(id(9), "Hemoglobin", "13.5", "g/dL", "12-16");
        var payload = new LabResultV2Payload(id(7), id(4), id(3), id(5),
                CareEpisodeType.ADMISSION, id(8), "CBC", 1, List.of(result),
                "No acute finding", id(6), LocalDate.parse("2026-09-27"),
                Instant.parse("2026-09-27T04:00:00Z"));
        var event = new DomainEventEnvelope<>(id(1), "lab.result.created", 1,
                Instant.parse("2026-09-27T04:00:00Z"), "correlation-123", "lab-service", payload);

        assertMatchesFixture("lab.result.created.admission.v1.json", event);
    }

    @Test
    void resultFixturesUseBusinessRevisionOneIndependentlyOfSchemaVersion() throws IOException {
        JsonNode outpatient = objectMapper.readTree(readFixture("lab.result.created.v1.json"));
        JsonNode admission = objectMapper.readTree(readFixture("lab.result.created.admission.v1.json"));

        assertThat(outpatient.path("version").asInt()).isEqualTo(1);
        assertThat(admission.path("version").asInt()).isEqualTo(1);
        assertThat(outpatient.at("/payload/resultVersion").asInt()).isEqualTo(1);
        assertThat(admission.at("/payload/resultVersion").asInt()).isEqualTo(1);
    }

    private void assertMatchesFixture(String fixtureName, DomainEventEnvelope<?> event) throws IOException {
        JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsBytes(event));
        JsonNode fixture = objectMapper.readTree(readFixture(fixtureName));

        assertThat(actual).as("wire event from %s", fixtureName).isEqualTo(fixture);
    }

    private byte[] readFixture(String fixtureName) throws IOException {
        try (var input = getClass().getResourceAsStream("/contracts/" + fixtureName)) {
            assertThat(input).as("canonical %s fixture", fixtureName).isNotNull();
            return input.readAllBytes();
        }
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
