package com.mediflow.report.infrastructure.messaging.carefinance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.mediflow.report.messaging.consumer.ReportEventValidationException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CareFinanceStrictWireTest {
    private final CareFinanceEnvelopeDecoder decoder = new CareFinanceEnvelopeDecoder(new ObjectMapper());
    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "trailing", "shortUuid", "uppercaseUuid", "oversize", "empty", "nullBody", "nullRouting", "correlation"})
    void decode_ambiguousOrUnboundedWireRejects(String kind) throws Exception {
        String json = Files.readString(Path.of("../clinical-service/src/test/resources/contracts/medicalrecord.completed.v1.json"));
        byte[] bytes = switch (kind) {
            case "duplicate" -> json.replace("\"version\": 1", "\"version\": 2, \"version\": 1").getBytes(StandardCharsets.UTF_8);
            case "trailing" -> (json + " {}").getBytes(StandardCharsets.UTF_8);
            case "shortUuid" -> json.replace("00000000-0000-4000-8000-000000000003", "0-0-4000-8000-3").getBytes(StandardCharsets.UTF_8);
            case "uppercaseUuid" -> json.replace("00000000-0000-4000-8000-000000000003", "ABCDEF00-0000-4000-8000-000000000003").getBytes(StandardCharsets.UTF_8);
            case "oversize" -> new byte[1_048_577];
            case "empty" -> new byte[0];
            case "nullBody" -> null;
            case "correlation" -> json.replaceAll("\"correlationId\"\\s*:\\s*\"[^\"]*\"", "\"correlationId\":\"" + "x".repeat(121) + "\"").getBytes(StandardCharsets.UTF_8);
            default -> json.getBytes(StandardCharsets.UTF_8);
        };
        assertThatThrownBy(() -> decoder.decode(kind.equals("nullRouting") ? null : "medicalrecord.completed", bytes))
                .isInstanceOf(ReportEventValidationException.class);
    }
}
