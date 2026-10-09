package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.infrastructure.messaging.SurgeryCancellationDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SurgeryCancellationContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SurgeryCancellationDecoder decoder = new SurgeryCancellationDecoder(mapper);

    @ParameterizedTest @ValueSource(strings = {"admission", "outpatient"})
    void decode_actualSurgeryProducer_preservesExactContextWithoutNarrative(String context) throws Exception {
        var c = decoder.decode("surgery.cancelled", fixture(context));
        assertThat(c.careEpisodeType()).isEqualTo(context.equals("admission") ? "ADMISSION" : "OUTPATIENT_VISIT");
        assertThat(c.sourceFingerprint()).hasSize(64);
        assertThat(c.cancelledAt().toString()).isEqualTo("2026-10-07T01:05:00Z");
        assertThat(c.toString()).doesNotContain("Patient request", "reason=");
    }
    @ParameterizedTest @ValueSource(strings = {"version", "producer", "sourceRevision", "caseRevision", "patientId", "admissionId", "reasonCode", "cancellationStage", "cancelledAt", "correlationId", "correctionId"})
    void decode_invalidContract_isSafePermanentFailure(String field) throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("admission"));
        var payload = (ObjectNode) root.get("payload");
        switch (field) {
            case "version" -> root.put(field, 2);
            case "producer" -> root.put(field, "billing-service");
            case "sourceRevision" -> payload.put(field, 2);
            case "caseRevision" -> payload.put(field, 0);
            case "patientId" -> payload.put(field, "1-1-1-1-1");
            case "admissionId" -> payload.put(field, UUID.randomUUID().toString());
            case "reasonCode" -> payload.put(field, "UNKNOWN");
            case "cancellationStage" -> payload.put(field, "AFTER_START");
            case "cancelledAt" -> payload.put(field, "2026-10-07T01:04:00Z");
            case "correlationId" -> root.put(field, "x".repeat(121));
            default -> payload.putNull(field);
        }
        assertThatThrownBy(() -> decoder.decode("surgery.cancelled", mapper.writeValueAsBytes(root)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid Surgery cancellation contract").hasNoCause();
    }
    @Test void decode_duplicateKeyTrailingDataAndWrongRoute_rejects() throws Exception {
        var body = new String(fixture("admission"), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> decoder.decode("surgery.cancelled", body.replace("\"version\": 1", "\"version\": 1, \"version\": 1").getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decode("surgery.cancelled", (body + " {}").getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decode("surgery.completed", fixture("admission"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void decode_newDelivery_hasSameBusinessFingerprint_butNarrativeChangeConflicts() throws Exception {
        var c = decoder.decode("surgery.cancelled", fixture("admission"));
        var root = (ObjectNode) mapper.readTree(fixture("admission"));
        root.put("eventId", UUID.randomUUID().toString());
        var second = decoder.decode("surgery.cancelled", mapper.writeValueAsBytes(root));
        assertThat(second.sourceFingerprint()).isEqualTo(c.sourceFingerprint());
        assertThat(second.deliveryFingerprint()).isNotEqualTo(c.deliveryFingerprint());
        ((ObjectNode) root.get("payload")).put("reason", "Different private narrative");
        assertThat(decoder.decode("surgery.cancelled", mapper.writeValueAsBytes(root)).sourceFingerprint()).isNotEqualTo(c.sourceFingerprint());
    }
    @Test void decode_producerAllowsAccountActorWithoutStaff_nullableStaffIsNotInvented() throws Exception {
        var root=(ObjectNode)mapper.readTree(fixture("admission")); ((ObjectNode)root.get("payload")).putNull("cancelledByStaffId");
        var command=decoder.decode("surgery.cancelled",mapper.writeValueAsBytes(root));
        assertThat(command.cancelledBy()).isNotNull(); assertThat(command.cancelledByStaffId()).isNull();
    }
    static byte[] fixture(String context) throws Exception {
        Path path = Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.cancelled." + context + ".v1.json");
        if (!Files.exists(path)) path = Path.of("backend/surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.cancelled." + context + ".v1.json");
        return Files.readAllBytes(path);
    }
}
