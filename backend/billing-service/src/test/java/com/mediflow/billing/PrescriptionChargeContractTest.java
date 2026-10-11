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

class PrescriptionChargeContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final PrescriptionChargeDecoder decoder = new PrescriptionChargeDecoder(mapper);

    static byte[] fixture() throws Exception {
        return Files.readAllBytes(Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.created.v1.json"));
    }
    static byte[] outpatientFixture() throws Exception {
        return Files.readAllBytes(Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.created.outpatient.v1.json"));
    }
    @Test void decode_originalOutpatientBytes_recordIsNotInferredEpisode() throws Exception {
        var command = decoder.decode("prescription.created", outpatientFixture());
        assertThat(command.careContext()).isEqualTo("OUTPATIENT");
        assertThat(command.admissionId()).isNull();
        assertThat(command.recordId()).isNotEqualTo(command.careEpisodeId());
    }
    @Test void decode_originalPharmacyBytes_exactAdmissionAndServerPriceSnapshot() throws Exception {
        var command = decoder.decode("prescription.created", fixture());
        assertThat(command.careContext()).isEqualTo("ADMISSION");
        assertThat(command.admissionId()).isEqualTo(command.careEpisodeId());
        assertThat(command.items()).hasSize(1);
        assertThat(command.totalAmount()).isEqualByComparingTo("2000.00");
        assertThat(command.items().getFirst().unitPrice()).isEqualByComparingTo("1000.00");
    }
    @ParameterizedTest @ValueSource(strings = {"wrongProducer", "wrongVersion", "wrongSource", "wrongAdmission", "wrongTotal", "negativePrice", "fractionalQuantity", "duplicateDrug", "abbreviatedUuid", "extraField", "reason", "secondTime", "zeroTotal", "overflow", "blankCorrelation", "controlCorrelation"})
    void decode_invalidSnapshot_rejectsBeforePersistence(String mutation) throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture()); var p = (ObjectNode) root.get("payload");
        var item = (ObjectNode) p.path("items").get(0);
        switch (mutation) {
            case "wrongProducer" -> root.put("producer", "billing-service");
            case "wrongVersion" -> root.put("version", 0);
            case "wrongSource" -> p.put("sourceId", "20000000-0000-4000-8000-000000000001");
            case "wrongAdmission" -> p.put("admissionId", "20000000-0000-4000-8000-000000000004");
            case "wrongTotal" -> p.put("totalAmount", 2001);
            case "negativePrice" -> item.put("unitPrice", -1000);
            case "fractionalQuantity" -> item.put("quantity", 2.5);
            case "duplicateDrug" -> ((com.fasterxml.jackson.databind.node.ArrayNode) p.get("items")).add(item.deepCopy());
            case "abbreviatedUuid" -> item.put("drugId", "1-1-1-1-1");
            case "extraField" -> p.put("diagnosis", "not a charge field");
            case "reason" -> p.put("reason", "not creation");
            case "secondTime" -> p.put("filledAt", p.path("createdAt").asText());
            case "zeroTotal" -> { item.put("unitPrice", 0); p.put("totalAmount", 0); }
            case "overflow" -> { item.put("unitPrice", new java.math.BigDecimal("99999999999999999.00")); p.put("totalAmount", new java.math.BigDecimal("199999999999999998.00")); }
            case "blankCorrelation" -> root.put("correlationId", " ");
            case "controlCorrelation" -> root.put("correlationId", "bad\ntrace");
        }
        assertThatThrownBy(() -> decoder.decode("prescription.created", mapper.writeValueAsBytes(root))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void decode_duplicateKeysTrailingTokensAndWrongRouting_rejects() throws Exception {
        String body = new String(fixture(), java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> decoder.decode("prescription.created", (body + "{}").getBytes())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decode("prescription.created", body.replace("\"version\": 1", "\"version\": 1, \"version\": 1").getBytes())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decode("prescription.filled", fixture())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decode("prescription.created", new byte[1_048_577])).isInstanceOf(IllegalArgumentException.class);
    }
}
