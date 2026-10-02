package com.mediflow.report.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;

class LabOperationalContributionMapperTest {
    private final CareFinanceEnvelopeDecoder decoder = new CareFinanceEnvelopeDecoder(new ObjectMapper());
    private final LabOperationalContributionMapper mapper = new LabOperationalContributionMapper(ZoneId.of("Asia/Bangkok"));

    @Test
    void map_actualLabFixture_usesExactSourceFieldsNotEnvelopeRevision() throws IOException {
        var event = fixture("lab.result.created.v1.json");
        var fact = mapper.map(event).contributions().get(0);
        assertThat(fact.sourceId()).isEqualTo(event.metadata().sourceId());
        assertThat(fact.sourceRevision()).isOne();
        assertThat(fact.metric()).isEqualTo(Metric.LAB_TESTS);
        assertThat(fact.departmentId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000005"));
        assertThat(fact.careEpisodeId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000006"));
        assertThat(fact.occurredAt()).isEqualTo(Instant.parse("2026-09-28T06:00:00Z"));
    }

    @Test
    void map_syntheticResultVersionThree_rejectsUntilCorrectionPolicyExists() throws IOException {
        var event = fixture("lab.result.created.admission.v1.json");
        assertThat(event.metadata().version()).isOne();
        assertThat(event.payload().get("resultVersion")).isEqualTo(1);
        var payload = new LinkedHashMap<>(event.payload());
        payload.put("resultVersion", 3);
        var unsupportedCorrection = new DecodedCareFinanceEvent(event.metadata(), payload);
        assertThatThrownBy(() -> mapper.map(unsupportedCorrection)).hasMessageContaining("corrections");
    }

    @Test
    void map_republishedEvent_usesCompletionTimeForMidnightBoundary() throws IOException {
        var event = fixture("lab.result.created.v1.json");
        var payload = new LinkedHashMap<>(event.payload());
        payload.put("completedAt", "2026-09-28T17:00:00.123456789Z");
        payload.put("performedDate", "2026-09-27");
        var metadata = event.metadata();
        var republished = new CareFinanceEventMetadata(metadata.eventId(), metadata.eventType(), 1,
                Instant.parse("2026-10-02T00:00:00Z"), metadata.correlationId(), metadata.producer(),
                metadata.sourceField(), metadata.sourceId());
        var fact = mapper.map(new DecodedCareFinanceEvent(republished, payload)).contributions().get(0);
        assertThat(fact.metricDate()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(fact.occurredAt().getNano()).isEqualTo(123456789);
    }

    @ParameterizedTest
    @ValueSource(strings = {"labId", "resultVersion", "departmentId", "careEpisodeType", "careEpisodeId", "completedAt"})
    void map_missingSourceField_neverUsesFallback(String missing) throws IOException {
        var event = fixture("lab.result.created.v1.json");
        var payload = new LinkedHashMap<>(event.payload());
        payload.remove(missing);
        assertThatThrownBy(() -> mapper.map(new DecodedCareFinanceEvent(event.metadata(), payload)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void map_fractionalRevisionOrWrongLabId_rejects() throws IOException {
        var event = fixture("lab.result.created.v1.json");
        var payload = new LinkedHashMap<>(event.payload());
        payload.put("resultVersion", 1.5);
        assertThatThrownBy(() -> mapper.map(new DecodedCareFinanceEvent(event.metadata(), payload)))
                .hasMessageContaining("integral");
        payload.put("resultVersion", 1);
        payload.put("labId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> mapper.map(new DecodedCareFinanceEvent(event.metadata(), payload)))
                .hasMessageContaining("identity");
    }

    private static byte[] producerFixture(String name) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("backend/report-service/pom.xml"))) {
            current = current.getParent();
        }
        if (current == null) throw new IOException("Repository root not found");
        return Files.readAllBytes(current.resolve("backend/lab-service/src/test/resources/contracts/" + name));
    }

    private DecodedCareFinanceEvent fixture(String name) throws IOException {
        return decoder.decode("lab.result.created", producerFixture(name));
    }
}
