package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.infrastructure.messaging.AdmissionChargeCloseDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdmissionChargeCloseDecoderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AdmissionChargeCloseDecoder decoder = new AdmissionChargeCloseDecoder(mapper);

    static byte[] fixture() throws Exception {
        return Files.readAllBytes(Path.of("../inpatient-service/src/test/resources/contracts/discharge.medically.approved.v1.json"));
    }

    @Test void acceptsActualUnchangedInpatientBytes() throws Exception {
        var command = decoder.decode(AdmissionChargeCloseDecoder.ROUTING_KEY, fixture());
        assertThat(command.admissionId().toString()).endsWith("000021");
        assertThat(command.approvedAtIso()).isEqualTo("2026-09-30T09:00:00Z");
        assertThat(command.sourceFingerprint()).hasSize(64);
    }

    @ParameterizedTest @ValueSource(strings = {"version", "producer", "eventType", "patientId", "occurredAt", "narrative", "revision", "missing"})
    void rejectsUnsupportedOrAmbiguousProofWithoutPayloadInException(String mutation) throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(fixture());
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("payload");
        switch (mutation) {
            case "version" -> root.put("version", 2);
            case "producer" -> root.put("producer", "billing-service");
            case "eventType" -> root.put("eventType", "admission.closed");
            case "patientId" -> payload.put("patientId", "1-1-1-1-1");
            case "occurredAt" -> root.put("occurredAt", "2026-09-30T10:00:00Z");
            case "narrative" -> payload.put("summary", "sensitive-clinical-text");
            case "revision" -> payload.put("sourceRevision", 2);
            case "missing" -> payload.remove("approvedBy");
            default -> throw new IllegalArgumentException();
        }
        assertThatThrownBy(() -> decoder.decode(AdmissionChargeCloseDecoder.ROUTING_KEY, mapper.writeValueAsBytes(root)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid Inpatient medical-discharge V1 contract");
    }

    @Test void changedDeliveryKeepsBusinessSourceButChangesDeliveryProof() throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(fixture());
        var original = decoder.decode(AdmissionChargeCloseDecoder.ROUTING_KEY, fixture());
        root.put("eventId", java.util.UUID.randomUUID().toString());
        var replay = decoder.decode(AdmissionChargeCloseDecoder.ROUTING_KEY, mapper.writeValueAsBytes(root));
        assertThat(replay.sourceFingerprint()).isEqualTo(original.sourceFingerprint());
        assertThat(replay.deliveryFingerprint()).isNotEqualTo(original.deliveryFingerprint());
    }
}
