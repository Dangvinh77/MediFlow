package com.mediflow.surgery.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase;
import com.mediflow.surgery.domain.model.SurgeryAuthorityChange.ReferenceKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurgeryAuthorityChangeDecoderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SurgeryAuthorityChangeDecoder decoder = new SurgeryAuthorityChangeDecoder(mapper);
    private static final Instant NOW = Instant.parse("2026-10-05T02:01:00Z");
    private static final String TYPE = ReceiveSurgeryAuthorityChangeUseCase.EVENT_TYPE;

    @ParameterizedTest @ValueSource(strings = {"room", "staff"})
    void decodesActualProducerOwnedFixturesAndPreservesRawBytes(String kind) throws Exception {
        byte[] bytes = fixture(kind);
        var command = decoder.decode(TYPE, bytes, NOW);
        assertThat(command.incoming().payload()).containsExactly(bytes);
        assertThat(command.change().referenceKind()).isEqualTo(kind.equals("room") ? ReferenceKind.ROOM : ReferenceKind.STAFF_CAPABILITY);
        assertThat(command.change().revision()).isEqualTo(2);
        assertThat(command.incoming().semanticKey()).isEqualTo(TYPE + ":" + command.incoming().eventId());
    }
    @Test void semanticReplayIgnoresOnlyDeliveryIdentityNotRevisionBusinessContent() throws Exception {
        var original = decoder.decode(TYPE, fixture("room"), NOW);
        var root = root("room");
        root.put("eventId", UUID.randomUUID().toString()); root.put("correlationId", UUID.randomUUID().toString());
        var replay = decoder.decode(TYPE, mapper.writeValueAsBytes(root), NOW);
        assertThat(replay.change().fingerprint()).isEqualTo(original.change().fingerprint());
        assertThat(replay.incoming().fingerprint()).isNotEqualTo(original.incoming().fingerprint());
        ((ObjectNode)root.path("payload")).put("reason", "Different authority decision");
        assertThat(decoder.decode(TYPE, mapper.writeValueAsBytes(root), NOW).change().fingerprint()).isNotEqualTo(original.change().fingerprint());
    }
    @ParameterizedTest @ValueSource(strings = {"eventId", "eventType", "version", "occurredAt", "correlationId", "producer", "payload"})
    void rejectsMissingEnvelopeField(String field) throws Exception {
        var root = root("room"); root.remove(field); reject(mapper.writeValueAsBytes(root));
    }
    @ParameterizedTest @ValueSource(strings = {"referenceKind", "referenceId", "teamRole", "revision", "actorAccountId", "reason"})
    void rejectsMissingPayloadField(String field) throws Exception {
        var root = root("room"); ((ObjectNode)root.path("payload")).remove(field); reject(mapper.writeValueAsBytes(root));
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.0", "9223372036854775808", "\"2\"", "null"})
    void rejectsInvalidSourceRevision(String value) throws Exception {
        var root = root("room"); ((ObjectNode)root.path("payload")).set("revision", mapper.readTree(value)); reject(mapper.writeValueAsBytes(root));
    }
    @ParameterizedTest @ValueSource(strings = {"producer", "eventType", "version", "eventId", "correlationId", "occurredAt"})
    void rejectsWrongEnvelopeValue(String field) throws Exception {
        var root = root("room");
        if (field.equals("version")) root.put(field, 2);
        else root.put(field, field.equals("occurredAt") ? NOW.plusSeconds(6).toString() : "not-valid");
        reject(mapper.writeValueAsBytes(root));
    }
    @ParameterizedTest @ValueSource(strings = {"referenceKind", "referenceId", "actorAccountId", "reason", "teamRole"})
    void rejectsWrongRoomPayload(String field) throws Exception {
        var root = root("room");
        ((ObjectNode)root.path("payload")).put(field, field.equals("teamRole") ? "PRIMARY_SURGEON" : field.equals("reason") ? " " : "not-valid");
        reject(mapper.writeValueAsBytes(root));
    }
    @Test void staffRequiresSupportedExactRoleAndTrimmedReason() throws Exception {
        var root = root("staff"); var payload = (ObjectNode)root.path("payload");
        payload.putNull("teamRole"); reject(mapper.writeValueAsBytes(root));
        payload.put("teamRole", "DOCTOR"); reject(mapper.writeValueAsBytes(root));
        payload.put("teamRole", "PRIMARY_SURGEON"); payload.put("reason", " Reason "); reject(mapper.writeValueAsBytes(root));
        payload.put("reason", "x".repeat(501)); reject(mapper.writeValueAsBytes(root));
    }
    @Test void rejectsDuplicatesTrailingTokensWrongRouteAndOversizedBody() throws Exception {
        reject("{\"eventId\":\"a\",\"eventId\":\"b\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        reject((new String(fixture("room"), java.nio.charset.StandardCharsets.UTF_8) + " {}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        reject(new byte[1_048_577]); reject(new byte[0]);
        assertThatThrownBy(() -> decoder.decode("other", fixture("room"), NOW)).isInstanceOf(IllegalArgumentException.class);
    }
    private void reject(byte[] bytes) {
        assertThatThrownBy(() -> decoder.decode(TYPE, bytes, NOW)).isInstanceOf(IllegalArgumentException.class).hasCause(null);
    }
    private ObjectNode root(String kind) throws Exception { return (ObjectNode)mapper.readTree(fixture(kind)); }
    static byte[] fixture(String kind) throws Exception {
        return Files.readAllBytes(Path.of("../organization-service/src/test/resources/contracts/surgery-authority-v1/event." + kind + ".changed.json"));
    }
}
