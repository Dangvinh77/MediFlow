package com.mediflow.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.domain.model.OperationalContribution;
import com.mediflow.report.domain.model.OperationalContribution.Metric;

class OperationalSnapshotCodecTest {
    private final OperationalSnapshotCodec codec = new OperationalSnapshotCodec(new ObjectMapper());
    private final UUID eventId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final Instant time = Instant.parse("2026-10-01T08:00:00.123456789Z");

    @Test
    void redactedSnapshotRoundTrip_preservesExactBusinessNanosAndDoesNotInventPayload() {
        var fact = fact();
        String snapshot = codec.snapshot(List.of(fact));
        var decoded = codec.decode(eventId, metadata(), snapshot, OperationalSnapshotCodec.hash(snapshot), 1);
        assertThat(decoded.contributions()).containsExactly(fact);
        assertThat(decoded.event().payload()).isEmpty();
        assertThat(snapshot).doesNotContain("eventId", "diagnosis", "patientId");
    }

    @Test
    void jsonbWhitespaceAndFieldOrder_doNotChangeCanonicalFingerprint() throws Exception {
        String snapshot = codec.snapshot(List.of(fact()));
        String pretty = new ObjectMapper().readTree(snapshot).toPrettyString();
        assertThat(codec.decode(eventId, metadata(), pretty, OperationalSnapshotCodec.hash(snapshot), 1).contributions()).hasSize(1);
    }

    @Test
    void changedBusinessNanosOrFingerprint_isRejected() {
        String snapshot = codec.snapshot(List.of(fact()));
        assertThatThrownBy(() -> codec.decode(eventId, metadata(), snapshot.replace("123456789", "123456788"),
                OperationalSnapshotCodec.hash(snapshot), 1)).hasMessageContaining("fingerprint");
    }

    @Test
    void eventIdentityAndProjectorVersion_areNotGuessed() {
        String snapshot = codec.snapshot(List.of(fact()));
        assertThatThrownBy(() -> codec.decode(UUID.randomUUID(), metadata(), snapshot, OperationalSnapshotCodec.hash(snapshot), 1))
                .hasMessageContaining("identity");
        assertThatThrownBy(() -> codec.decode(eventId, metadata(), snapshot, OperationalSnapshotCodec.hash(snapshot), 2))
                .hasMessageContaining("version");
    }

    @Test
    void extraFactFieldsAndStringRevision_areRejected() {
        String snapshot = codec.snapshot(List.of(fact()));
        assertThatThrownBy(() -> codec.decode(eventId, metadata(), snapshot.replace("\"category\":", "\"diagnosis\":\"private\",\"category\":"),
                OperationalSnapshotCodec.hash(snapshot), 1)).hasMessageContaining("fields");
        assertThatThrownBy(() -> codec.decode(eventId, metadata(), snapshot.replace("\"sourceRevision\":1", "\"sourceRevision\":\"1\""),
                OperationalSnapshotCodec.hash(snapshot), 1)).hasMessageContaining("integral");
    }

    @Test
    void deliveryIdentityIsExcludedButBusinessIdentityIsIncluded() {
        var first = fact();
        var republished = new OperationalContribution(UUID.randomUUID(), first.sourceType(), first.sourceId(), 1,
                first.metric(), departmentId, null, null, first.metricDate(), first.value(), null, time);
        assertThat(codec.fingerprint(first)).isEqualTo(codec.fingerprint(republished));
        Map<String, Object> canonical = new LinkedHashMap<>(codec.factFields(first));
        canonical.put("sourceId", UUID.randomUUID().toString());
        assertThat(OperationalSnapshotCodec.hash(codec.json(canonical))).isNotEqualTo(codec.fingerprint(first));
    }

    private OperationalContribution fact() {
        return new OperationalContribution(eventId, "MEDICAL_RECORD", sourceId, 1, Metric.COMPLETED_VISITS,
                departmentId, null, null, LocalDate.of(2026, 10, 1), BigDecimal.ONE, null, time);
    }

    private String metadata() {
        return codec.json(Map.of("eventId", eventId.toString(), "eventType", "medicalrecord.completed", "version", 1,
                "occurredAt", time.toString(), "correlationId", "trace", "producer", "clinical-service",
                "sourceField", "recordId", "sourceId", sourceId.toString()));
    }
}
