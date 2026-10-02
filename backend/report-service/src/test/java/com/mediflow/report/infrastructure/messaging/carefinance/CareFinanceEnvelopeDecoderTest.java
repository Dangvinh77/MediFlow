package com.mediflow.report.infrastructure.messaging.carefinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.messaging.consumer.ReportEventValidationException;

class CareFinanceEnvelopeDecoderTest {

    private static final String EVENT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SOURCE_ID = "22222222-2222-2222-2222-222222222222";

    private final CareFinanceEnvelopeDecoder decoder =
            new CareFinanceEnvelopeDecoder(new ObjectMapper().findAndRegisterModules());

    @Test
    void decode_supportedContracts_extractsCanonicalSourceForEachEvent() {
        List<EventCase> supportedEvents = List.of(
                new EventCase("payment.completed", "billing-service", "transactionId"),
                new EventCase("payment.refunded", "billing-service", "refundTransactionId"),
                new EventCase("settlement.completed", "billing-service", "settlementId"),
                new EventCase("medicalrecord.completed", "clinical-service", "recordId"),
                new EventCase("admission.started", "inpatient-service", "admissionId"),
                new EventCase("admission.closed", "inpatient-service", "admissionId"),
                new EventCase("lab.result.created", "lab-service", "labId"),
                new EventCase("prescription.filled", "pharmacy-service", "dispenseId"));

        for (EventCase eventCase : supportedEvents) {
            var decoded = decoder.decode(eventCase.eventType(),
                    envelope(eventCase.eventType(), 1, eventCase.producer(),
                            eventCase.sourceField(), SOURCE_ID));

            assertThat(decoded.metadata().eventId()).isEqualTo(UUID.fromString(EVENT_ID));
            assertThat(decoded.metadata().eventType()).isEqualTo(eventCase.eventType());
            assertThat(decoded.metadata().occurredAt())
                    .isEqualTo(Instant.parse("2026-09-28T01:00:00Z"));
            assertThat(decoded.metadata().sourceField()).isEqualTo(eventCase.sourceField());
            assertThat(decoded.metadata().sourceId()).isEqualTo(UUID.fromString(SOURCE_ID));
        }
    }

    @Test
    void decode_paymentRefund_usesRefundTransactionAsCanonicalSource() {
        var decoded = decoder.decode("payment.refunded",
                envelope("payment.refunded", 1, "billing-service", "refundTransactionId", SOURCE_ID));

        assertThat(decoded.metadata().sourceId()).isEqualTo(UUID.fromString(SOURCE_ID));
        assertThat(decoded.payload()).containsEntry("refundTransactionId", SOURCE_ID);
    }

    @Test
    void decode_clinicalProducerFixture_usesCanonicalRecordIdFromExactBytes() throws IOException {
        byte[] producerBytes = readProducerFixture(
                "backend/clinical-service/src/test/resources/contracts/medicalrecord.completed.v1.json");

        var decoded = decoder.decode("medicalrecord.completed", producerBytes);

        assertThat(decoded.metadata().producer()).isEqualTo("clinical-service");
        assertThat(decoded.metadata().sourceField()).isEqualTo("recordId");
        assertThat(decoded.metadata().sourceId())
                .isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000003"));
        assertThat(decoded.payload()).containsEntry("disposition", "ADMISSION");
    }

    @Test
    void decode_labProducerFixture_usesCanonicalLabIdFromExactBytes() throws IOException {
        byte[] producerBytes = readProducerFixture(
                "backend/lab-service/src/test/resources/contracts/lab.result.created.v1.json");

        var decoded = decoder.decode("lab.result.created", producerBytes);

        assertThat(decoded.metadata().producer()).isEqualTo("lab-service");
        assertThat(decoded.metadata().sourceField()).isEqualTo("labId");
        assertThat(decoded.metadata().sourceId())
                .isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000002"));
        assertThat(decoded.payload()).containsEntry("resultVersion", 1);
    }

    @Test
    void decode_labAdmissionProducerFixture_preservesExactEpisodeFromProducerBytes() throws IOException {
        byte[] producerBytes = readProducerFixture(
                "backend/lab-service/src/test/resources/contracts/lab.result.created.admission.v1.json");

        var decoded = decoder.decode("lab.result.created", producerBytes);

        assertThat(decoded.metadata().producer()).isEqualTo("lab-service");
        assertThat(decoded.metadata().sourceField()).isEqualTo("labId");
        assertThat(decoded.metadata().sourceId())
                .isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000007"));
        assertThat(decoded.payload())
                .containsEntry("careEpisodeType", "ADMISSION")
                .containsEntry("careEpisodeId", "00000000-0000-4000-8000-000000000008")
                .containsEntry("recordId", "00000000-0000-4000-8000-000000000003");
        assertThat(decoded.payload().get("careEpisodeId"))
                .isNotEqualTo(decoded.payload().get("recordId"));
    }

    @Test
    void decode_inpatientLifecycleProducerFixtures_preserveAdmissionIdentityFromExactBytes() throws IOException {
        byte[] startedBytes = readProducerFixture(
                "backend/inpatient-service/src/test/resources/contracts/admission.started.v1.json");
        byte[] closedBytes = readProducerFixture(
                "backend/inpatient-service/src/test/resources/contracts/admission.closed.v1.json");

        var started = decoder.decode("admission.started", startedBytes);
        var closed = decoder.decode("admission.closed", closedBytes);

        UUID expectedAdmissionId = UUID.fromString("00000000-0000-4000-8000-000000000021");
        assertThat(started.metadata().producer()).isEqualTo("inpatient-service");
        assertThat(started.metadata().sourceField()).isEqualTo("admissionId");
        assertThat(started.metadata().sourceId()).isEqualTo(expectedAdmissionId);
        assertThat(started.payload())
                .containsEntry("admissionId", expectedAdmissionId.toString())
                .containsEntry("patientId", "00000000-0000-4000-8000-000000000022")
                .containsEntry("departmentId", "00000000-0000-4000-8000-000000000023")
                .containsEntry("admittedAt", "2026-09-28T02:10:00Z");

        assertThat(closed.metadata().producer()).isEqualTo("inpatient-service");
        assertThat(closed.metadata().sourceField()).isEqualTo("admissionId");
        assertThat(closed.metadata().sourceId()).isEqualTo(expectedAdmissionId);
        assertThat(closed.payload())
                .containsEntry("admissionId", expectedAdmissionId.toString())
                .containsEntry("settlementId", "00000000-0000-4000-8000-000000000027")
                .containsEntry("closedAt", "2026-10-01T11:00:00Z");
    }

    @Test
    void decode_unsupportedVersion_rejectsWithoutDowngrade() {
        assertThatThrownBy(() -> decoder.decode("payment.completed",
                envelope("payment.completed", 2, "billing-service", "transactionId", SOURCE_ID)))
                .isInstanceOf(ReportEventValidationException.class)
                .hasMessageContaining("version 1");
    }

    @Test
    void decode_unknownEventType_rejects() {
        assertThatThrownBy(() -> decoder.decode("surgery.completed",
                envelope("surgery.completed", 1, "surgery-service", "resultId", SOURCE_ID)))
                .isInstanceOf(ReportEventValidationException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void decode_wrongProducer_rejects() {
        assertThatThrownBy(() -> decoder.decode("medicalrecord.completed",
                envelope("medicalrecord.completed", 1, "report-service", "recordId", SOURCE_ID)))
                .isInstanceOf(ReportEventValidationException.class)
                .hasMessageContaining("Producer");
    }

    @Test
    void decode_routingKeyMismatch_rejects() {
        assertThatThrownBy(() -> decoder.decode("payment.completed",
                envelope("payment.refunded", 1, "billing-service", "refundTransactionId", SOURCE_ID)))
                .isInstanceOf(ReportEventValidationException.class)
                .hasMessageContaining("routing key");
    }

    @Test
    void decode_missingCanonicalSource_rejects() {
        String body = """
                {"eventId":"%s","eventType":"medicalrecord.completed","version":1,
                 "occurredAt":"2026-09-28T01:00:00Z","correlationId":"cid-1",
                 "producer":"clinical-service","payload":{"departmentId":"%s"}}
                """.formatted(EVENT_ID, SOURCE_ID);

        assertThatThrownBy(() -> decoder.decode("medicalrecord.completed",
                body.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ReportEventValidationException.class)
                .hasMessageContaining("recordId");
    }

    @Test
    void decode_nonUuidCanonicalSource_rejects() {
        assertThatThrownBy(() -> decoder.decode("lab.result.created",
                envelope("lab.result.created", 1, "lab-service", "labId", "not-a-uuid")))
                .isInstanceOf(ReportEventValidationException.class)
                .hasMessageContaining("labId must be a UUID");
    }

    private static byte[] envelope(
            String eventType, int version, String producer, String sourceField, String sourceId) {
        String body = """
                {"eventId":"%s","eventType":"%s","version":%d,
                 "occurredAt":"2026-09-28T01:00:00Z","correlationId":"cid-1",
                 "producer":"%s","payload":{"%s":"%s"}}
                """.formatted(EVENT_ID, eventType, version, producer, sourceField, sourceId);
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readProducerFixture(String repositoryRelativePath) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("backend/report-service/pom.xml"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IOException("Could not locate MediFlow repository root from the test working directory");
        }
        return Files.readAllBytes(current.resolve(repositoryRelativePath));
    }

    private record EventCase(String eventType, String producer, String sourceField) {
    }
}
