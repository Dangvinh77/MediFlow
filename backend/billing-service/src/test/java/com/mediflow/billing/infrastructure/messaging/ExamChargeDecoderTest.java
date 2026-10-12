package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.command.ExamChargeCommand;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Billing phải đọc đúng byte producer của clinical-service — không copy/đổi tên field
 * (HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE). Đọc trực tiếp bản sao fixture thật tại
 * {@code src/test/resources/contracts/appointment-status-changed-v1/} (mirror của
 * {@code backend/clinical-service/src/test/resources/contracts/appointment.status.changed.v1.json}).
 */
class ExamChargeDecoderTest {
    private static final String DIR = "contracts/appointment-status-changed-v1/";
    private final ExamChargeDecoder decoder = new ExamChargeDecoder(new ObjectMapper());

    @Test
    void checkInFixture_decodesExactExamChargeTrigger() throws Exception {
        var command = decoder.decodeApplicable("appointment.status.changed", bytes("appointment.status.changed.v1.json"));

        assertThat(command).isPresent();
        ExamChargeCommand exam = command.get();
        assertThat(exam.appointmentId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000002"));
        assertThat(exam.patientId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000003"));
        assertThat(exam.departmentId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000004"));
        assertThat(exam.careEpisodeType()).isEqualTo("OUTPATIENT_VISIT");
        assertThat(exam.careEpisodeId()).isEqualTo(exam.appointmentId());
        assertThat(exam.priceCode()).isEqualTo("OUTPATIENT_EXAM");
    }

    @Test
    void wrongRoutingKey_rejected() throws Exception {
        byte[] body = bytes("appointment.status.changed.v1.json");
        assertThatThrownBy(() -> decoder.decodeApplicable("medicalrecord.created", body))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otherValidTransition_isNotApplicable() {
        String json = """
                {
                  "eventId": "00000000-0000-4000-8000-000000000011",
                  "eventType": "appointment.status.changed",
                  "version": 1,
                  "occurredAt": "2026-10-10T09:10:00Z",
                  "correlationId": "00000000-0000-4000-8000-000000000007",
                  "producer": "clinical-service",
                  "payload": {
                    "appointmentId": "00000000-0000-4000-8000-000000000002",
                    "recordId": "00000000-0000-4000-8000-000000000008",
                    "patientId": "00000000-0000-4000-8000-000000000003",
                    "departmentId": "00000000-0000-4000-8000-000000000004",
                    "oldStatus": "AWAITING_PAYMENT",
                    "newStatus": "IN_EXAM",
                    "careEpisodeType": "OUTPATIENT_VISIT",
                    "careEpisodeId": "00000000-0000-4000-8000-000000000002",
                    "sourceType": "EXAM",
                    "sourceId": "00000000-0000-4000-8000-000000000002",
                    "priceCode": "OUTPATIENT_EXAM",
                    "changedAt": "2026-10-10T09:10:00Z",
                    "emergencyOverrideId": null
                  }
                }
                """;
        var command = decoder.decodeApplicable("appointment.status.changed", json.getBytes(StandardCharsets.UTF_8));
        assertThat(command).isEmpty();
    }

    @Test
    void chargeTriggerShapeWithUnexpectedRecordId_rejected() {
        String json = """
                {
                  "eventId": "00000000-0000-4000-8000-000000000012",
                  "eventType": "appointment.status.changed",
                  "version": 1,
                  "occurredAt": "2026-10-10T09:00:00Z",
                  "correlationId": "00000000-0000-4000-8000-000000000007",
                  "producer": "clinical-service",
                  "payload": {
                    "appointmentId": "00000000-0000-4000-8000-000000000002",
                    "recordId": "00000000-0000-4000-8000-000000000008",
                    "patientId": "00000000-0000-4000-8000-000000000003",
                    "departmentId": "00000000-0000-4000-8000-000000000004",
                    "oldStatus": "PENDING",
                    "newStatus": "AWAITING_PAYMENT",
                    "careEpisodeType": "OUTPATIENT_VISIT",
                    "careEpisodeId": "00000000-0000-4000-8000-000000000002",
                    "sourceType": "EXAM",
                    "sourceId": "00000000-0000-4000-8000-000000000002",
                    "priceCode": "OUTPATIENT_EXAM",
                    "changedAt": "2026-10-10T09:00:00Z",
                    "emergencyOverrideId": null
                  }
                }
                """;
        assertThatThrownBy(() -> decoder.decodeApplicable("appointment.status.changed",
                json.getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] bytes(String fileName) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(DIR + fileName)) {
            assertThat(in).as("fixture " + fileName + " phải tồn tại trong test resources").isNotNull();
            return in.readAllBytes();
        }
    }
}
