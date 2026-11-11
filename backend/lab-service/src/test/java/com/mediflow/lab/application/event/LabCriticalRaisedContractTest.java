package com.mediflow.lab.application.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.lab.domain.model.CareEpisodeType;

/** The producer's payload has exactly the fields of the shared fixture consumers test against. */
class LabCriticalRaisedContractTest {
    @Test void payloadMatchesTheFixture() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        var payload = json.valueToTree(new LabCriticalRaisedPayload(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CareEpisodeType.OUTPATIENT_VISIT,
                UUID.randomUUID(), "Điện giải", 1, "K", "Kali", "7.2", "mmol/L", "CRITICAL_HIGH", Instant.now()));
        var fixture = json.readTree(Files.readString(Path.of("src/test/resources/contracts/lab.critical.raised.v1.json")))
                .get("payload");
        List<String> keys = new ArrayList<>();
        payload.fieldNames().forEachRemaining(keys::add);
        List<String> expected = new ArrayList<>();
        fixture.fieldNames().forEachRemaining(expected::add);
        assertThat(keys).containsExactlyInAnyOrderElementsOf(expected);
    }
}
