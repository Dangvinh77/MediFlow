package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.infrastructure.messaging.SurgeryCompletionDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SurgeryCompletionDecoderTest {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private final SurgeryCompletionDecoder decoder=new SurgeryCompletionDecoder(mapper);
    static byte[] fixture(String episode)throws Exception {
        return Files.readAllBytes(Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.completed."+episode+".v1.json"));
    }
    @ParameterizedTest @ValueSource(strings={"admission","outpatient"})
    void originalSurgeryBytesPinEpisodeResultAndExactPerformedTime(String episode)throws Exception {
        var command=decoder.decode("surgery.completed",fixture(episode));
        assertThat(command.sourceRevision()).isOne(); assertThat(command.caseRevision()).isEqualTo(5);
        assertThat(command.completedAtIso()).isEqualTo("2026-10-07T01:10:30Z");
        assertThat(command.sourceFingerprint()).hasSize(64);
    }
    @ParameterizedTest @ValueSource(strings={"version","sourceRevision","producer","episode","time","uuid","missing","unknown","duplicateItem","moneyQuantity"})
    void invalidOrAmbiguousSourceHasNoCompatibilityFallback(String mutation)throws Exception {
        var root=(ObjectNode)mapper.readTree(fixture("admission")); var payload=(ObjectNode)root.path("payload");
        switch(mutation) {
            case "version"->root.put("version",2);
            case "sourceRevision"->payload.put("sourceRevision",2);
            case "producer"->root.put("producer","billing-service");
            case "episode"->payload.put("careEpisodeId",java.util.UUID.randomUUID().toString());
            case "time"->payload.put("completedAt","2026-10-07T02:00:00Z");
            case "uuid"->payload.put("resultId","1-1-1-1-1");
            case "missing"->payload.remove("sourceRevision");
            case "unknown"->payload.put("performedAmount",100);
            case "duplicateItem"->((com.fasterxml.jackson.databind.node.ArrayNode)payload.path("performedItems")).add(payload.path("performedItems").get(0).deepCopy());
            case "moneyQuantity"->((ObjectNode)payload.path("performedItems").get(0)).put("quantity",0.00001);
            default->throw new IllegalArgumentException();
        }
        assertThatThrownBy(()->decoder.decode("surgery.completed",mapper.writeValueAsBytes(root)))
                .hasMessage("Invalid Surgery completed V1 contract");
    }
    @Test void duplicateJsonKeysAndTrailingContentReject()throws Exception {
        String raw=new String(fixture("admission"),StandardCharsets.UTF_8);
        assertThatThrownBy(()->decoder.decode("surgery.completed",raw.replace("\"version\": 1","\"version\": 1, \"version\": 1").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->decoder.decode("surgery.completed",(raw+" {}").getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void wrongRoutingAndOversizeInputRejectWithoutRawPayload() {
        assertThatThrownBy(()->decoder.decode("surgery.cancelled",new byte[1])).hasMessage("Invalid Surgery completed V1 contract");
        assertThatThrownBy(()->decoder.decode("surgery.completed",new byte[1_048_577])).hasMessage("Invalid Surgery completed V1 contract");
    }
}
