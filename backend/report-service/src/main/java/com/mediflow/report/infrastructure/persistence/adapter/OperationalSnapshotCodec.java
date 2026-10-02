package com.mediflow.report.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.OperationalProjectionPlanner;
import com.mediflow.report.domain.model.OperationalContribution;

/** Canonical V7 aggregate snapshots, not a reconstructed producer envelope. */
public final class OperationalSnapshotCodec {
    private static final Set<String> EVENT_FIELDS = Set.of("eventId", "eventType", "version", "occurredAt",
            "correlationId", "producer", "sourceField", "sourceId");
    private static final Set<String> FACT_FIELDS = Set.of("sourceType", "sourceId", "sourceRevision", "metric",
            "departmentId", "careEpisodeType", "careEpisodeId", "metricDate", "value", "category", "occurredAt");
    private final ObjectMapper mapper;

    public OperationalSnapshotCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public String snapshot(List<OperationalContribution> facts) {
        return json(OperationalProjectionPlanner.ordered(facts).stream().map(this::factFields).toList());
    }

    public String fingerprint(OperationalContribution fact) { return hash(json(factFields(fact))); }

    public Map<String, Object> factFields(OperationalContribution fact) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sourceType", fact.sourceType());
        fields.put("sourceId", fact.sourceId().toString());
        fields.put("sourceRevision", fact.sourceRevision());
        fields.put("metric", fact.metric().name());
        fields.put("departmentId", fact.departmentId().toString());
        fields.put("careEpisodeType", fact.careEpisodeType());
        fields.put("careEpisodeId", fact.careEpisodeId() == null ? null : fact.careEpisodeId().toString());
        fields.put("metricDate", fact.metricDate().toString());
        fields.put("value", fact.value());
        fields.put("category", fact.category());
        fields.put("occurredAt", fact.occurredAt().toString());
        return fields;
    }

    public ApplyOperationalContributionCommand decode(UUID eventId, String eventJson, String factsJson,
            String projectionFingerprint, int projectorVersion) {
        if (projectorVersion != 1) throw new IllegalArgumentException("Unsupported operational projector version");
        try {
            var event = mapper.readTree(eventJson);
            exactFields(event, EVENT_FIELDS);
            var metadata = new CareFinanceEventMetadata(UUID.fromString(text(event, "eventId")),
                    text(event, "eventType"), integer(event, "version"), Instant.parse(text(event, "occurredAt")),
                    text(event, "correlationId"), text(event, "producer"), text(event, "sourceField"),
                    UUID.fromString(text(event, "sourceId")));
            if (!metadata.eventId().equals(eventId)) throw new IllegalArgumentException("Replay journal identity mismatch");
            var array = mapper.readTree(factsJson);
            if (!array.isArray() || array.isEmpty() || array.size() > OperationalContribution.Metric.values().length) {
                throw new IllegalArgumentException("Invalid replay fact array");
            }
            var facts = new ArrayList<OperationalContribution>();
            for (var fact : array) {
                exactFields(fact, FACT_FIELDS);
                if (!fact.get("value").isNumber()) throw new IllegalArgumentException("Replay value must be numeric");
                facts.add(new OperationalContribution(eventId, text(fact, "sourceType"),
                        UUID.fromString(text(fact, "sourceId")), integer(fact, "sourceRevision"),
                        OperationalContribution.Metric.valueOf(text(fact, "metric")),
                        UUID.fromString(text(fact, "departmentId")), nullableText(fact, "careEpisodeType"),
                        fact.get("careEpisodeId").isNull() ? null : UUID.fromString(text(fact, "careEpisodeId")),
                        LocalDate.parse(text(fact, "metricDate")), new BigDecimal(fact.get("value").asText()),
                        nullableText(fact, "category"), Instant.parse(text(fact, "occurredAt"))));
            }
            var command = new ApplyOperationalContributionCommand(new DecodedCareFinanceEvent(metadata, Map.of()), facts);
            if (!hash(snapshot(facts)).equals(projectionFingerprint)) {
                throw new IllegalArgumentException("Replay projection fingerprint mismatch");
            }
            // No raw payload exists in the journal; do not call the live envelope journal writer.
            return command;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Malformed operational replay snapshot", exception);
        }
    }

    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Cannot encode operational snapshot", exception); }
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void exactFields(JsonNode node, Set<String> expected) {
        var actual = new java.util.HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!node.isObject() || !actual.equals(expected)) throw new IllegalArgumentException("Invalid operational snapshot fields");
    }

    private static String text(JsonNode node, String field) {
        if (!node.get(field).isTextual() || node.get(field).asText().isBlank()) {
            throw new IllegalArgumentException("Replay text field required: " + field);
        }
        return node.get(field).asText();
    }

    private static String nullableText(JsonNode node, String field) {
        return node.get(field).isNull() ? null : text(node, field);
    }

    private static int integer(JsonNode node, String field) {
        if (!node.get(field).isIntegralNumber() || !node.get(field).canConvertToInt()) {
            throw new IllegalArgumentException("Replay integral field required: " + field);
        }
        return node.get(field).intValue();
    }
}
