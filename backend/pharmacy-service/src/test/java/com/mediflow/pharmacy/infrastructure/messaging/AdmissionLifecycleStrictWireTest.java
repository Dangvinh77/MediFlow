package com.mediflow.pharmacy.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdmissionLifecycleStrictWireTest {
    private final AdmissionLifecycleDecoder decoder = new AdmissionLifecycleDecoder(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "trailing", "shortUuid", "uppercaseUuid", "large", "empty", "nullBody", "nullRouting", "correlation"})
    void admissionWire_ambiguousOrUnboundedInputIsRejected(String variant) throws Exception {
        String original = Files.readString(Path.of("../inpatient-service/src/test/resources/contracts/admission.started.v1.json"));
        String value = switch (variant) {
            case "duplicate" -> original.replaceFirst("\\{", "{\"version\":2,");
            case "trailing" -> original + " {}";
            case "shortUuid" -> original.replace("00000000-0000-4000-8000-000000000021", "1-1-1-1-1");
            case "uppercaseUuid" -> original.replace("00000000-0000-4000-8000-000000000021", "AAAAAAAA-0000-4000-8000-000000000021");
            case "large" -> " ".repeat(1_048_577);
            case "empty" -> "";
            case "correlation" -> original.replace("\"correlationId\":", "\"ignoredCorrelation\":\"old\",\"correlationId\":\"" + "x".repeat(121) + "\",\"unused\":");
            default -> original;
        };
        byte[] body = variant.equals("nullBody") ? null : value.getBytes(StandardCharsets.UTF_8);
        String key = variant.equals("nullRouting") ? null : "admission.started";
        assertThatThrownBy(() -> decoder.decode(key, body)).isInstanceOf(IllegalArgumentException.class);
    }
}
