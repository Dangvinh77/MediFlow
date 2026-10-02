package com.mediflow.inpatient.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.inpatient.application.dto.event.AdmissionClosedEvent;
import com.mediflow.inpatient.application.dto.event.AdmissionDepositRequestedEvent;
import com.mediflow.inpatient.application.dto.event.AdmissionStartedEvent;
import com.mediflow.inpatient.application.dto.event.DomainEventEnvelope;
import com.mediflow.inpatient.application.dto.event.MedicalDischargeApprovedEvent;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InpatientEventWireMapperContractFixtureTest {

    private static final String CORRELATION_ID = id(17).toString();

    private final InpatientEventWireMapper mapper = new InpatientEventWireMapper();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void admissionDepositRequestedMatchesCanonicalV1Fixture() throws IOException {
        var event = new DomainEventEnvelope<>(id(11), "admission.deposit.requested", 1,
                Instant.parse("2026-09-28T02:05:00Z"), CORRELATION_ID, "inpatient-service",
                new AdmissionDepositRequestedEvent(id(21), id(22), id(23), "ADMISSION", id(21),
                        "ADMISSION_DEPOSIT", id(21), "INPATIENT_DEPOSIT", new BigDecimal("150000.00"),
                        "Initial admission deposit"));

        assertMatchesFixture("admission.deposit.requested.v1.json", event);
    }

    @Test
    void admissionStartedMatchesCanonicalV1Fixture() throws IOException {
        var event = new DomainEventEnvelope<>(id(12), "admission.started", 1,
                Instant.parse("2026-09-28T02:10:00Z"), CORRELATION_ID, "inpatient-service",
                new AdmissionStartedEvent(id(21), id(22), id(24), id(23),
                        Instant.parse("2026-09-28T02:10:00Z"), false, null));

        assertMatchesFixture("admission.started.v1.json", event);
    }

    @Test
    void medicallyApprovedDischargeMatchesCanonicalV1Fixture() throws IOException {
        var event = new DomainEventEnvelope<>(id(13), "discharge.medically.approved", 1,
                Instant.parse("2026-09-30T09:00:00Z"), CORRELATION_ID, "inpatient-service",
                new MedicalDischargeApprovedEvent(id(21), id(22), id(25), id(26),
                        Instant.parse("2026-09-30T09:00:00Z")));

        assertMatchesFixture("discharge.medically.approved.v1.json", event);
    }

    @Test
    void admissionClosedMatchesCanonicalV1Fixture() throws IOException {
        var event = new DomainEventEnvelope<>(id(14), "admission.closed", 1,
                Instant.parse("2026-10-01T11:00:00Z"), CORRELATION_ID, "inpatient-service",
                new AdmissionClosedEvent(id(21), id(22), id(27), null,
                        Instant.parse("2026-10-01T11:00:00Z")));

        assertMatchesFixture("admission.closed.v1.json", event);
    }

    @Test
    void lifecycleFixturesKeepExactIdentityAndDistinctOrderedBusinessTimes() throws IOException {
        JsonNode started = objectMapper.readTree(readFixture("admission.started.v1.json"));
        JsonNode discharged = objectMapper.readTree(readFixture("discharge.medically.approved.v1.json"));
        JsonNode closed = objectMapper.readTree(readFixture("admission.closed.v1.json"));

        assertThat(discharged.at("/payload/admissionId")).isEqualTo(started.at("/payload/admissionId"));
        assertThat(closed.at("/payload/admissionId")).isEqualTo(started.at("/payload/admissionId"));
        assertThat(discharged.at("/payload/patientId")).isEqualTo(started.at("/payload/patientId"));
        assertThat(closed.at("/payload/patientId")).isEqualTo(started.at("/payload/patientId"));

        Instant admittedAt = Instant.parse(started.at("/payload/admittedAt").asText());
        Instant approvedAt = Instant.parse(discharged.at("/payload/approvedAt").asText());
        Instant closedAt = Instant.parse(closed.at("/payload/closedAt").asText());
        assertThat(admittedAt).isBefore(approvedAt);
        assertThat(approvedAt).isBefore(closedAt);
    }

    private void assertMatchesFixture(String fixtureName, DomainEventEnvelope<?> event) throws IOException {
        byte[] serialized = objectMapper.writeValueAsBytes(mapper.toWireEnvelope(event));
        JsonNode actual = objectMapper.readTree(serialized);
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
