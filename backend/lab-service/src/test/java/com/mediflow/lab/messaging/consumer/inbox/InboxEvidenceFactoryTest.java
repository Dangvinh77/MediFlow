package com.mediflow.lab.messaging.consumer.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.application.dto.command.ConsumerInboxClaim;

class InboxEvidenceFactoryTest {

    private final InboxEvidenceFactory factory = new InboxEvidenceFactory(new ObjectMapper());

    @Test
    void canonicalBusinessBytesSortObjectsAndDropOnlyVolatileEnvelopeFields() throws Exception {
        byte[] body = ("{\"payload\":{\"z\":2,\"a\":[{\"y\":1,\"x\":0}]},"
                + "\"producer\":\"clinical-service\",\"correlationId\":\"corr-1\","
                + "\"eventId\":\"00000000-0000-4000-8000-000000000001\","
                + "\"eventType\":\"medicalrecord.created\",\"version\":1,"
                + "\"occurredAt\":\"2026-01-01T00:00:00Z\"}").getBytes(StandardCharsets.UTF_8);

        ConsumerInboxClaim evidence = factory.create("lab-medical-record", "medicalrecord.created",
                UUID.fromString("00000000-0000-4000-8000-000000000001"), "MEDICAL_RECORD:record-1:v1", body);

        assertThat(evidence.rawBody()).containsExactly(body);
        assertThat(new String(evidence.canonicalBusinessBytes(), StandardCharsets.UTF_8))
                .isEqualTo("{\"eventType\":\"medicalrecord.created\",\"payload\":{\"a\":[{\"x\":0,\"y\":1}],"
                        + "\"z\":2},\"producer\":\"clinical-service\",\"version\":1}");
    }

    @Test
    void sameOperationWithDifferentVolatileEnvelopeValuesHasTheSameFingerprint() {
        byte[] first = ("{\"eventId\":\"00000000-0000-4000-8000-000000000001\","
                + "\"eventType\":\"medicalrecord.created\",\"version\":1,"
                + "\"occurredAt\":\"2026-01-01T00:00:00Z\",\"correlationId\":\"a\","
                + "\"producer\":\"clinical-service\",\"payload\":{\"recordId\":\"record-1\"}}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] replay = ("{\"eventId\":\"00000000-0000-4000-8000-000000000002\","
                + "\"eventType\":\"medicalrecord.created\",\"version\":1,"
                + "\"occurredAt\":\"2026-01-02T00:00:00Z\",\"correlationId\":\"b\","
                + "\"producer\":\"clinical-service\",\"payload\":{\"recordId\":\"record-1\"}}")
                .getBytes(StandardCharsets.UTF_8);

        var original = factory.create("lab-medical-record", "medicalrecord.created",
                UUID.fromString("00000000-0000-4000-8000-000000000001"), "MEDICAL_RECORD:record-1:v1", first);
        var duplicateOperation = factory.create("lab-medical-record", "medicalrecord.created",
                UUID.fromString("00000000-0000-4000-8000-000000000002"), "MEDICAL_RECORD:record-1:v1", replay);

        assertThat(duplicateOperation.canonicalBusinessBytes())
                .containsExactly(original.canonicalBusinessBytes());
    }

    @Test
    void paymentLabTestIdsAreFingerprintedAsAnUnorderedSet() {
        UUID eventA = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID eventB = UUID.fromString("00000000-0000-4000-8000-000000000002");
        String invoice = "00000000-0000-4000-8000-000000000003";
        String first = "00000000-0000-4000-8000-000000000004";
        String second = "00000000-0000-4000-8000-000000000005";
        byte[] bodyA = ("{\"eventId\":\"" + eventA + "\",\"invoiceId\":\"" + invoice
                + "\",\"labTestIds\":[\"" + first + "\",\"" + second + "\"]}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] bodyB = ("{\"eventId\":\"" + eventB + "\",\"invoiceId\":\"" + invoice
                + "\",\"labTestIds\":[\"" + second + "\",\"" + first + "\"]}")
                .getBytes(StandardCharsets.UTF_8);

        var original = factory.create("lab-payment", "payment.completed", eventA,
                "lab:payment:v0:" + invoice, bodyA);
        var replay = factory.create("lab-payment", "payment.completed", eventB,
                "lab:payment:v0:" + invoice, bodyB);

        assertThat(replay.canonicalBusinessBytes()).containsExactly(original.canonicalBusinessBytes());
    }

    @Test
    void rejectsBodyWhoseEventIdDoesNotMatchTheValidatedEnvelope() {
        byte[] body = "{\"eventId\":\"00000000-0000-4000-8000-000000000002\",\"payload\":{}}"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> factory.create("lab-medical-record", "medicalrecord.created",
                UUID.fromString("00000000-0000-4000-8000-000000000001"), "MEDICAL_RECORD:record-1:v1", body))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOversizedRawEvidence() {
        byte[] body = new byte[InboxEvidenceFactory.MAX_BODY_BYTES + 1];

        assertThatThrownBy(() -> factory.create("lab-medical-record", "medicalrecord.created",
                UUID.randomUUID(), "MEDICAL_RECORD:record-1:v1", body))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
