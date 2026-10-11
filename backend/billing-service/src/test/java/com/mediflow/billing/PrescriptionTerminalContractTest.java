package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.infrastructure.messaging.PrescriptionChargeDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PrescriptionTerminalContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final PrescriptionChargeDecoder decoder = new PrescriptionChargeDecoder(mapper);

    static byte[] fixture(String key) throws Exception {
        return Files.readAllBytes(Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/" + key + ".v1.json"));
    }

    @ParameterizedTest @ValueSource(strings = {"prescription.filled", "prescription.cancelled", "prescription.expired", "prescription.dispense.failed"})
    void decode_actualProducerTerminal_sameImmutableCreationSnapshot(String key) throws Exception {
        var terminal = decoder.decodeTerminal(key, fixture(key));
        var creation = decoder.decode("prescription.created", PrescriptionChargeContractTest.fixture());
        assertThat(terminal.snapshot().snapshotFingerprint()).isEqualTo(creation.snapshotFingerprint());
        assertThat(terminal.eventType()).isEqualTo(key);
        assertThat(terminal.snapshot().careContext()).isEqualTo("ADMISSION");
    }

    @ParameterizedTest @ValueSource(strings = {"time", "unknown", "producer", "version", "missingReason", "createdAt", "wrongSource", "total", "correction", "controlReason"})
    void decode_invalidTerminal_rejectsWithoutLegacyFallback(String mutation) throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("prescription.cancelled"));
        var p = (ObjectNode) root.path("payload");
        switch (mutation) {
            case "time" -> root.put("occurredAt", "2026-10-02T00:00:00Z");
            case "unknown" -> p.put("diagnosis", "not accepted");
            case "producer" -> root.put("producer", "billing-service");
            case "version" -> root.put("version", 2);
            case "missingReason" -> p.remove("reason");
            case "createdAt" -> p.put("createdAt", root.path("occurredAt").asText());
            case "wrongSource" -> p.put("sourceId", java.util.UUID.randomUUID().toString());
            case "total" -> p.put("totalAmount", 1);
            case "correction" -> p.put("supersedesEventId", java.util.UUID.randomUUID().toString());
            case "controlReason" -> p.put("reason", "bad\nreason");
        }
        assertThatThrownBy(() -> decoder.decodeTerminal("prescription.cancelled", mapper.writeValueAsBytes(root)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void decode_terminalHash_separatesSourceFromDeliveryAndCreation() throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("prescription.expired"));
        var original = decoder.decodeTerminal("prescription.expired", mapper.writeValueAsBytes(root));
        root.put("eventId", java.util.UUID.randomUUID().toString()).put("correlationId", "replay");
        var replay = decoder.decodeTerminal("prescription.expired", mapper.writeValueAsBytes(root));
        assertThat(replay.snapshot().sourceFingerprint()).isEqualTo(original.snapshot().sourceFingerprint());
        assertThat(replay.snapshot().deliveryFingerprint()).isNotEqualTo(original.snapshot().deliveryFingerprint());
        ((ObjectNode)root.path("payload")).put("reason", "OTHER_REASON");
        var changed = decoder.decodeTerminal("prescription.expired", mapper.writeValueAsBytes(root));
        assertThat(changed.snapshot().sourceFingerprint()).isNotEqualTo(original.snapshot().sourceFingerprint());
        assertThat(changed.snapshot().snapshotFingerprint()).isEqualTo(original.snapshot().snapshotFingerprint());
    }
}
