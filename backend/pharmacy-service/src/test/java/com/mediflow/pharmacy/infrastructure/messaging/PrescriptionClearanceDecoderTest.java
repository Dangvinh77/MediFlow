package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** These inline examples test the consumer specification, NOT Billing producer approval. */
class PrescriptionClearanceDecoderTest {
    @ParameterizedTest
    @ValueSource(strings = {"exam", "lab", "admission", "surgery"})
    void decodeApplicable_validOtherPurposeProducerBytes_areNotApplicable(String purpose) throws Exception {
        var bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "../billing-service/src/test/resources/contracts/ledger-v1/clearance-" + purpose + ".json"));
        assertThat(decoder.decodeApplicable(PrescriptionClearanceDecoder.EVENT_TYPE, bytes)).isEmpty();
    }

    @Test
    void decodeApplicable_malformedOtherPurpose_isNotSilentlyAcknowledged() throws Exception {
        var bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "../billing-service/src/test/resources/contracts/ledger-v1/clearance-surgery.json"));
        var root = (ObjectNode) mapper.readTree(bytes);
        ((ObjectNode) root.get("payload")).remove("surgeryCaseId");
        assertThatThrownBy(() -> decoder.decodeApplicable(PrescriptionClearanceDecoder.EVENT_TYPE,
                mapper.writeValueAsBytes(root))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decodeApplicable(PrescriptionClearanceDecoder.EVENT_TYPE, new byte[1_048_577]))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void decode_billingProducerBytes_matchesExactPrescriptionAndGrantTime() throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "../billing-service/src/test/resources/contracts/ledger-v1/clearance-prescription.json"));
        var command = decoder.decode(PrescriptionClearanceDecoder.EVENT_TYPE, bytes);
        assertThat(command.clearance().prescriptionId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000004"));
        assertThat(command.clearance().grantedAt()).isEqualTo(java.time.Instant.parse("2026-10-05T08:00:00Z"));
        assertThat(command.clearance().expiresAt()).isNull();
    }
    private final ObjectMapper mapper = new ObjectMapper();
    private final PrescriptionClearanceDecoder decoder = new PrescriptionClearanceDecoder(mapper);

    @Test
    void decode_exactPrescriptionGrant_keepsMoneyPrecisionAndNanoseconds() throws Exception {
        var root = example();
        ((ObjectNode) root.get("payload")).put("amount", new BigDecimal("99999999999999999.99"));
        var command = decoder.decode(PrescriptionClearanceDecoder.EVENT_TYPE, mapper.writeValueAsBytes(root));
        assertThat(command.clearance().amount()).isEqualByComparingTo("99999999999999999.99");
        assertThat(command.clearance().grantedAt().getNano()).isEqualTo(123456789);
        assertThat(command.eventFingerprint()).hasSize(64);
    }

    @ParameterizedTest
    @ValueSource(strings = {"appointmentId", "recordId", "admissionId", "surgeryCaseId"})
    void decode_unrelatedTarget_rejects(String field) throws Exception {
        var root = example();
        ((ObjectNode) root.get("payload")).put(field, UUID.randomUUID().toString());
        reject(root);
    }

    @ParameterizedTest
    @ValueSource(strings = {"clearanceId", "invoiceId", "accountId", "patientId", "prescriptionId", "careEpisodeId"})
    void decode_missingExactIdentity_rejects(String field) throws Exception {
        var root = example();
        ((ObjectNode) root.get("payload")).remove(field);
        reject(root);
    }

    @Test
    void decode_wrongPurposeVersionProducerAndEpisode_rejects() throws Exception {
        var root = example();
        root.put("version", 0); reject(root);
        root = example(); root.put("producer", "pharmacy-service"); reject(root);
        root = example(); ((ObjectNode) root.get("payload")).put("purpose", "EXAM"); reject(root);
        root = example(); ((ObjectNode) root.get("payload")).put("careEpisodeType", "ADMISSION"); reject(root);
        root = example(); ((ObjectNode) root.get("payload")).put("emergencyOverride", true); reject(root);
        root = example(); ((ObjectNode) root.get("payload")).putArray("labTestIds").add(UUID.randomUUID().toString());
        reject(root);
        byte[] valid = mapper.writeValueAsBytes(example());
        assertThatThrownBy(() -> decoder.decode("payment.completed", valid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_duplicateKeysOrTrailingJson_rejects() throws Exception {
        String valid = mapper.writeValueAsString(example());
        assertThatThrownBy(() -> decoder.decode(PrescriptionClearanceDecoder.EVENT_TYPE,
                (valid + " {}").getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        String ambiguous = valid.replace("\"version\":1", "\"version\":0,\"version\":1");
        assertThatThrownBy(() -> decoder.decode(PrescriptionClearanceDecoder.EVENT_TYPE,
                ambiguous.getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void decode_abbreviatedUuidAndUnsupportedPaymentMethod_rejectWithoutFallback() throws Exception {
        var root = example();
        ((ObjectNode) root.get("payload")).put("prescriptionId", "0-0-0-0-4");
        reject(root);
        root = example();
        ((ObjectNode) root.get("payload")).put("paymentMethod", "INSURANCE");
        reject(root);
    }

    @Test
    void decode_newEventSamePayload_preservesSemanticFingerprint() throws Exception {
        var root = example();
        var first = decoder.decode(PrescriptionClearanceDecoder.EVENT_TYPE, mapper.writeValueAsBytes(root));
        root.put("eventId", UUID.randomUUID().toString());
        var second = decoder.decode(PrescriptionClearanceDecoder.EVENT_TYPE, mapper.writeValueAsBytes(root));
        assertThat(second.clearance()).isEqualTo(first.clearance());
        assertThat(second.eventFingerprint()).isNotEqualTo(first.eventFingerprint());
        ((ObjectNode) root.get("payload")).put("additiveField", "changed");
        var changed = decoder.decode(PrescriptionClearanceDecoder.EVENT_TYPE, mapper.writeValueAsBytes(root));
        assertThat(changed.clearance().payloadFingerprint()).isNotEqualTo(first.clearance().payloadFingerprint());
    }

    private void reject(ObjectNode root) throws Exception {
        byte[] body = mapper.writeValueAsBytes(root);
        assertThatThrownBy(() -> decoder.decode(PrescriptionClearanceDecoder.EVENT_TYPE, body))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ObjectNode example() {
        var root = mapper.createObjectNode();
        root.put("eventId", UUID.randomUUID().toString()).put("eventType", PrescriptionClearanceDecoder.EVENT_TYPE)
                .put("version", 1).put("producer", "billing-service").put("correlationId", "consumer-test")
                .put("occurredAt", "2026-10-01T03:00:00.123456789Z");
        var payload = root.putObject("payload");
        for (String field : new String[]{"clearanceId", "invoiceId", "accountId", "patientId", "prescriptionId", "careEpisodeId"}) {
            payload.put(field, UUID.randomUUID().toString());
        }
        payload.put("purpose", "PRESCRIPTION").put("careEpisodeType", "OUTPATIENT_VISIT")
                .put("amount", new BigDecimal("200.00")).put("currency", "VND").put("paymentMethod", "CASH")
                .putNull("expiresAt").put("emergencyOverride", false);
        return root;
    }
}
