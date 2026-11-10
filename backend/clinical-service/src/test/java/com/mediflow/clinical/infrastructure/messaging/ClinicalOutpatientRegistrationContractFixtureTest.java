package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.OutpatientRegistrationFact;
import com.mediflow.clinical.application.event.OutpatientRegistrationFact.CancellationFact;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientReservationStatus;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalOutpatientRegistrationContractFixtureTest {
    private final ClinicalOutpatientRegistrationEventSerializer serializer =
            new ClinicalOutpatientRegistrationEventSerializer(new ObjectMapper().findAndRegisterModules());

    @Test
    void awaitingPaymentFactMatchesProducerWireBytes() throws IOException {
        assertBytes("/contracts/outpatient-scheduling-v2/registration.awaiting_payment.v1.json", event(
                1001, 1101, fact(1, OutpatientReservationStatus.HELD,
                        OutpatientRegistrationStatus.AWAITING_PAYMENT, null, null, 0)));
    }

    @Test
    void confirmedFactMatchesProducerWireBytes() throws IOException {
        assertBytes("/contracts/outpatient-scheduling-v2/registration.confirmed.v1.json", event(
                1002, 1102, fact(3, OutpatientReservationStatus.CONFIRMED,
                        OutpatientRegistrationStatus.CONFIRMED, 1, null, 1)));
    }

    @Test
    void cancelledFactMatchesProducerWireBytesAndRetainsActorTombstone() throws IOException {
        assertBytes("/contracts/outpatient-scheduling-v2/registration.cancelled.v1.json", event(
                1003, 1103, fact(2, OutpatientReservationStatus.CANCELLED,
                        OutpatientRegistrationStatus.CANCELLED, null,
                        new CancellationFact(id(9001), id(9002), "patient request",
                                Instant.parse("2026-10-26T03:02:00Z")), 2)));
    }

    @Test
    void factWithoutRescheduleKeepsExactExistingJson() throws IOException {
        var plain = fact(3, OutpatientReservationStatus.CONFIRMED, OutpatientRegistrationStatus.CONFIRMED, 1, null, 1);
        assertThat(new String(serializer.serialize(event(1002, 1102, plain)), java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("reschedule");
        var moved = new OutpatientRegistrationFact(plain.registrationId(), plain.registrationRevision(), plain.patientId(), plain.doctorId(),
                plain.departmentId(), plain.sessionId(), plain.roomId(), plain.sessionDate(), plain.startTime(), plain.endTime(), plain.visitType(),
                plain.reservationId(), plain.reservationStatus(), plain.status(), plain.checkedInAt(), plain.queueNumber(), plain.reservationExpiresAt(),
                plain.priceCode(), plain.sourceType(), plain.sourceId(), plain.careEpisodeType(), plain.careEpisodeId(), null, plain.changedAt(),
                new OutpatientRegistrationFact.RescheduleFact(id(9001), id(9002), "Đổi bác sĩ", id(6002), id(4002), id(7002), LocalDate.of(2026, 10, 27),
                        LocalTime.of(8, 0), plain.changedAt()));
        var json = new ObjectMapper().readTree(serializer.serialize(event(1002, 1102, moved)));
        assertThat(json.get("payload").get("reschedule").get("previousSessionId").asText()).isEqualTo(id(6002).toString());
        assertThat(json.get("payload").get("reschedule").get("previousStartTime").asText()).isEqualTo("08:00");
    }

    private void assertBytes(String fixturePath, DomainEventEnvelope<OutpatientRegistrationFact> event)
            throws IOException {
        byte[] actual = serializer.serialize(event);
        try (var input = getClass().getResourceAsStream(fixturePath)) {
            assertThat(input).as("Clinical producer fixture " + fixturePath).isNotNull();
            assertThat(actual).containsExactly(input.readAllBytes());
        }
    }

    private static DomainEventEnvelope<OutpatientRegistrationFact> event(
            int eventId, int correlationId, OutpatientRegistrationFact fact) {
        return new DomainEventEnvelope<>(id(eventId), "outpatient.registration.changed", 1,
                fact.changedAt(), id(correlationId).toString(), "clinical-service", fact);
    }

    private static OutpatientRegistrationFact fact(int revision, OutpatientReservationStatus reservationStatus,
            OutpatientRegistrationStatus status, Integer queueNumber, CancellationFact cancellation, int minute) {
        Instant changedAt = Instant.parse("2026-10-26T03:%02d:00Z".formatted(minute));
        return new OutpatientRegistrationFact(
                id(2001), revision, id(3001), id(4001), id(5001), id(6001), id(7001),
                LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), LocalTime.of(10, 0),
                OutpatientVisitType.NEW_VISIT, id(8001), reservationStatus, status,
                Instant.parse("2026-10-26T03:00:00Z"),
                queueNumber, Instant.parse("2026-10-26T03:15:00Z"), "OUTPATIENT_EXAM", "EXAM", id(2001),
                "OUTPATIENT_VISIT", id(2001), cancellation, changedAt);
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
