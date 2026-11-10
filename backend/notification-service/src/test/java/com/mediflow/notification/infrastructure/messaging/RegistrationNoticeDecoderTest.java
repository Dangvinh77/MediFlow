package com.mediflow.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistrationNoticeDecoderTest {
    private final RegistrationNoticeDecoder decoder = new RegistrationNoticeDecoder(new ObjectMapper());

    private static String reschedule(String extra) {
        return """
                ,"reschedule":{"actorAccountId":"%s","actorStaffId":null,"reason":"Bác sĩ bận","previousSessionId":"%s","previousDoctorId":"%s","previousRoomId":"%s",
                "previousSessionDate":"2026-11-10","previousStartTime":"08:00","rescheduledAt":"2026-11-10T01:00:00Z"%s}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), extra);
    }

    private static byte[] wire(String reschedule) {
        UUID registration = UUID.randomUUID();
        return """
                {"eventId":"%s","eventType":"outpatient.registration.changed","version":1,"occurredAt":"2026-11-10T01:00:00Z","correlationId":"trace","producer":"clinical-service",
                "payload":{"registrationId":"%s","registrationRevision":3,"patientId":"%s","doctorId":"%s","departmentId":"%s","sessionId":"%s","roomId":"%s",
                "sessionDate":"2026-11-10","startTime":"09:00","endTime":"10:00","visitType":"NEW_VISIT","reservationId":"%s","reservationStatus":"CONFIRMED","status":"CONFIRMED",
                "checkedInAt":null,"queueNumber":4,"reservationExpiresAt":"2026-11-10T02:00:00Z","priceCode":"EXAM","sourceType":"EXAM","sourceId":"%s",
                "careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"%s","cancellation":null,"changedAt":"2026-11-10T01:00:00Z"%s}}
                """.formatted(UUID.randomUUID(), registration, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), registration, registration, reschedule).getBytes(StandardCharsets.UTF_8);
    }

    @Test void decoderAcceptsRescheduleWithExactFields() {
        assertThat(decoder.decode(wire(reschedule(""))).rescheduled()).isTrue();
    }

    @Test void decoderRejectsUnknownRescheduleField() {
        assertThatThrownBy(() -> decoder.decode(wire(reschedule(",\"extra\":1")))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void decoderStillAcceptsFactWithoutReschedule() {
        assertThat(decoder.decode(wire("")).rescheduled()).isFalse();
    }
}
