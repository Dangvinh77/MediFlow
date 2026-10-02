package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.Payload;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;

class PrescriptionCareEventCodecTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final PrescriptionCareEventCodec codec = new PrescriptionCareEventCodec(mapper);
    private final Instant now = Instant.parse("2026-10-01T08:00:00Z");

    @ParameterizedTest @EnumSource(EventType.class)
    void proposedCanonicalFixtureBytes_roundTripThroughRealCodec(EventType type) throws Exception {
        String path = "/contracts/care-finance-v1/" + type.routingKey() + ".v1.json";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            byte[] body = stream.readAllBytes();
            var decoded = codec.decode(type.routingKey(), body);
            assertThat(mapper.readTree(codec.encode(decoded))).isEqualTo(mapper.readTree(body));
        }
    }

    @ParameterizedTest @EnumSource(EventType.class)
    void allFiveAdmissionEvents_roundTripExactContextAndSourceSnapshot(EventType type) throws Exception {
        var event = event(type, CareContext.ADMISSION);
        byte[] bytes = codec.encode(event);
        assertThat(codec.decode(type.routingKey(), bytes)).isEqualTo(event);
        var json = mapper.readTree(bytes);
        assertThat(json.path("version").asInt()).isOne();
        assertThat(json.path("producer").asText()).isEqualTo("pharmacy-service");
        assertThat(json.path("occurredAt").isTextual()).isTrue();
        assertThat(json.path("payload").path("admissionId").asText()).isEqualTo(event.payload().careEpisodeId().toString());
        assertThat(json.has("prescriptionId")).isFalse();
        assertThat(json.path("payload").has("recordId")).isFalse();
        if (type == EventType.FILLED) {
            assertThat(json.path("payload").path("dispenseId").asText()).isEqualTo(event.payload().dispenseId().toString());
        }
    }

    @ParameterizedTest @EnumSource(EventType.class)
    void allFiveOutpatientEvents_allowMissingRecordWithoutInferringItFromEpisode(EventType type) {
        var event = event(type, CareContext.OUTPATIENT);
        var decoded = codec.decode(type.routingKey(), codec.encode(event));
        assertThat(decoded.payload().recordId()).isNull();
        assertThat(decoded.payload().admissionId()).isNull();
        assertThat(decoded.payload().careEpisodeId()).isEqualTo(event.payload().careEpisodeId());
    }

    @Test
    void wrongVersionProducerRoutingAndLegacyFlatShape_areRejected() throws Exception {
        String json = new String(codec.encode(event(EventType.CREATED, CareContext.OUTPATIENT)), java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> codec.decode("prescription.created", json.replace("\"version\":1", "\"version\":2").getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("prescription.created", json.replace("pharmacy-service", "billing-service").getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("prescription.filled", json.getBytes())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("prescription.created", "{\"prescriptionId\":\"legacy\"}".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filledWithoutDispenseIdAndMismatchedPriceTotal_areRejected() throws Exception {
        var filledRoot = mapper.readTree(codec.encode(event(EventType.FILLED, CareContext.OUTPATIENT)));
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) filledRoot.get("payload");
        payload.remove("dispenseId");
        assertThatThrownBy(() -> codec.decode("prescription.filled", mapper.writeValueAsBytes(filledRoot)))
                .isInstanceOf(IllegalArgumentException.class);
        var root = mapper.readTree(codec.encode(event(EventType.CREATED, CareContext.OUTPATIENT)));
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.get("payload")).put("totalAmount", "1.00");
        var changed = root;
        assertThatThrownBy(() -> codec.decode("prescription.created", mapper.writeValueAsBytes(changed)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PrescriptionCareEvent event(EventType type, CareContext context) {
        UUID prescriptionId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        boolean admission = context == CareContext.ADMISSION;
        var payload = new Payload(prescriptionId, null, admission ? episodeId : null, UUID.randomUUID(), UUID.randomUUID(),
                context, admission ? CareEpisodeType.ADMISSION : CareEpisodeType.OUTPATIENT_VISIT, episodeId,
                "PRESCRIPTION", prescriptionId, "RX-DEFAULT", List.of(new PrescriptionCareEvent.Item(UUID.randomUUID(),
                "Paracetamol", 2, new BigDecimal("1000.00"))), new BigDecimal("2000.00"),
                type == EventType.FILLED ? UUID.randomUUID() : null, type == EventType.CREATED ? now : null,
                type == EventType.FILLED ? now : null, type == EventType.DISPENSE_FAILED ? now : null,
                type == EventType.CANCELLED ? now : null, type == EventType.EXPIRED ? now : null,
                type == EventType.CREATED || type == EventType.FILLED ? null : "RESERVATION_RELEASED");
        return new PrescriptionCareEvent(UUID.randomUUID(), type, 1, now, "trace", "pharmacy-service", payload);
    }
}
