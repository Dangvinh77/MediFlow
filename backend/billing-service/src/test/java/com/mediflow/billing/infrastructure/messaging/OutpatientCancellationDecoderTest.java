package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutpatientCancellationDecoderTest {
    private static final String DIR = "contracts/appointment-exam-cancelled-v1/";
    private final OutpatientCancellationDecoder decoder = new OutpatientCancellationDecoder(new ObjectMapper());

    @Test
    void clinicalExamCancellationFixtureDecodesExactSourceAndAuditFact() throws Exception {
        var command = decoder.decode("appointment.exam.cancelled", bytes());

        assertThat(command.eventId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        assertThat(command.sourceType()).isEqualTo("EXAM");
        assertThat(command.sourceId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000002"));
        assertThat(command.sourceOrderId()).isNull();
        assertThat(command.patientId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000003"));
        assertThat(command.departmentId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000004"));
        assertThat(command.careEpisodeType()).isEqualTo("OUTPATIENT_VISIT");
        assertThat(command.careEpisodeId()).isEqualTo(command.sourceId());
        assertThat(command.cancelledBy()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000005"));
        assertThat(command.reason()).isEqualTo("Patient declined before examination");
        assertThat(command.cancelledAt()).isEqualTo(Instant.parse("2026-10-10T09:00:00Z"));
    }

    @Test
    void clinicalExamFixtureRejectsLabRoutingKey() throws Exception {
        assertThatThrownBy(() -> decoder.decode("lab.request.cancelled", bytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] bytes() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(
                DIR + "appointment.exam.cancelled.v1.json")) {
            assertThat(in).as("Clinical EXAM cancellation fixture must exist").isNotNull();
            return in.readAllBytes();
        }
    }
}
