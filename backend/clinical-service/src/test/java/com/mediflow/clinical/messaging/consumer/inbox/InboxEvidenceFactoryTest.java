package com.mediflow.clinical.messaging.consumer.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;

class InboxEvidenceFactoryTest {

    private final InboxEvidenceFactory factory = new InboxEvidenceFactory(new ObjectMapper());

    @Test
    void prescriptionV1PriceSnapshotsRetainDecimalPrecision() {
        UUID eventId = UUID.randomUUID();
        String prefix = "{\"eventId\":\"" + eventId + "\",\"payload\":{\"totalAmount\":";
        var first = factory.create("clinical-prescription", "prescription.filled", eventId,
                "clinical:prescription:v1:" + UUID.randomUUID(),
                (prefix + "999999999999999.99}}").getBytes(StandardCharsets.UTF_8));
        var changed = factory.create("clinical-prescription", "prescription.filled", eventId,
                first.operationKey(),
                (prefix + "999999999999999.98}}").getBytes(StandardCharsets.UTF_8));
        assertThat(first.canonicalBusinessBytes()).isNotEqualTo(changed.canonicalBusinessBytes());
    }

    @Test
    void canonicalBusinessBytesSortObjectsAndDropOnlyVolatileEnvelopeFields() throws Exception {
        byte[] body = ("{\"payload\":{\"z\":2,\"a\":[{\"y\":1,\"x\":0}]},"
                + "\"producer\":\"lab-service\",\"correlationId\":\"corr-1\","
                + "\"eventId\":\"00000000-0000-4000-8000-000000000001\","
                + "\"eventType\":\"lab.result.created\",\"version\":1,"
                + "\"occurredAt\":\"2026-01-01T00:00:00Z\"}").getBytes(StandardCharsets.UTF_8);

        ConsumerInboxClaim evidence = factory.create("clinical-lab-result", "lab.result.created",
                UUID.fromString("00000000-0000-4000-8000-000000000001"), "LAB_RESULT:abc:1", body);

        assertThat(evidence.rawBody()).containsExactly(body);
        assertThat(new String(evidence.canonicalBusinessBytes(), StandardCharsets.UTF_8))
                .isEqualTo("{\"eventType\":\"lab.result.created\",\"payload\":{\"a\":[{\"x\":0,\"y\":1}],"
                        + "\"z\":2},\"producer\":\"lab-service\",\"version\":1}");
    }

    @Test
    void sameOperationWithDifferentVolatileEnvelopeValuesHasTheSameFingerprint() {
        byte[] first = ("{\"eventId\":\"00000000-0000-4000-8000-000000000001\","
                + "\"eventType\":\"lab.result.created\",\"version\":1,"
                + "\"occurredAt\":\"2026-01-01T00:00:00Z\",\"correlationId\":\"a\","
                + "\"producer\":\"lab-service\",\"payload\":{\"labId\":\"lab-1\"}}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] replay = ("{\"eventId\":\"00000000-0000-4000-8000-000000000002\","
                + "\"eventType\":\"lab.result.created\",\"version\":1,"
                + "\"occurredAt\":\"2026-01-02T00:00:00Z\",\"correlationId\":\"b\","
                + "\"producer\":\"lab-service\",\"payload\":{\"labId\":\"lab-1\"}}")
                .getBytes(StandardCharsets.UTF_8);

        var original = factory.create("clinical-lab-result", "lab.result.created",
                UUID.fromString("00000000-0000-4000-8000-000000000001"), "LAB_RESULT:lab-1:1", first);
        var duplicateOperation = factory.create("clinical-lab-result", "lab.result.created",
                UUID.fromString("00000000-0000-4000-8000-000000000002"), "LAB_RESULT:lab-1:1", replay);

        assertThat(duplicateOperation.canonicalBusinessBytes())
                .containsExactly(original.canonicalBusinessBytes());
    }

    @Test
    void clearanceLabTestIdsAreFingerprintedAsAnUnorderedSet() {
        UUID eventA = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID eventB = UUID.fromString("00000000-0000-4000-8000-000000000002");
        String clearance = "00000000-0000-4000-8000-000000000003";
        String first = "00000000-0000-4000-8000-000000000004";
        String second = "00000000-0000-4000-8000-000000000005";
        byte[] bodyA = ("{\"eventId\":\"" + eventA + "\",\"eventType\":\"financial.clearance.granted\","
                + "\"payload\":{\"clearanceId\":\"" + clearance + "\",\"purpose\":\"LAB_TEST\","
                + "\"labTestIds\":[\"" + first + "\",\"" + second + "\"]}}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] bodyB = ("{\"eventId\":\"" + eventB + "\",\"eventType\":\"financial.clearance.granted\","
                + "\"payload\":{\"clearanceId\":\"" + clearance + "\",\"purpose\":\"LAB_TEST\","
                + "\"labTestIds\":[\"" + second + "\",\"" + first + "\"]}}")
                .getBytes(StandardCharsets.UTF_8);

        var original = factory.create("clinical-exam-clearance", "financial.clearance.granted", eventA,
                "clinical:clearance:v1:" + clearance, bodyA);
        var replay = factory.create("clinical-exam-clearance", "financial.clearance.granted", eventB,
                "clinical:clearance:v1:" + clearance, bodyB);

        assertThat(replay.canonicalBusinessBytes()).containsExactly(original.canonicalBusinessBytes());
    }

    @Test
    void rejectsBodyWhoseEventIdDoesNotMatchTheValidatedEnvelope() {
        byte[] body = "{\"eventId\":\"00000000-0000-4000-8000-000000000002\",\"payload\":{}}"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> factory.create("clinical-lab-result", "lab.result.created",
                UUID.fromString("00000000-0000-4000-8000-000000000001"), "LAB_RESULT:lab-1:1", body))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOversizedRawEvidence() {
        byte[] body = new byte[InboxEvidenceFactory.MAX_BODY_BYTES + 1];

        assertThatThrownBy(() -> factory.create("clinical-lab-result", "lab.result.created",
                UUID.randomUUID(), "clinical:lab-result:v1:00000000-0000-4000-8000-000000000001:1", body))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
