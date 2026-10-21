package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.application.event.AppointmentExamCancelledPayload;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalAppointmentExamCancelledContractFixtureTest {

    private static final Instant CANCELLED_AT = Instant.parse("2026-10-10T09:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void cancellationMatchesTheExactBillingEvidenceFixture() throws IOException {
        UUID appointmentId = id(2);
        var payload = new AppointmentExamCancelledPayload(appointmentId, id(3), id(4),
                CareEpisodeType.OUTPATIENT_VISIT, appointmentId, "EXAM", appointmentId,
                id(5), "Patient declined before examination", CANCELLED_AT);
        var event = new DomainEventEnvelope<>(id(1), "appointment.exam.cancelled", 1,
                CANCELLED_AT, id(7).toString(), "clinical-service", payload);

        JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsBytes(event));
        JsonNode fixture = objectMapper.readTree(readFixture());

        assertThat(actual).isEqualTo(fixture);
    }

    private byte[] readFixture() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/contracts/appointment-exam-cancelled-v1/appointment.exam.cancelled.v1.json")) {
            assertThat(input).as("canonical appointment.exam.cancelled fixture").isNotNull();
            return input.readAllBytes();
        }
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
