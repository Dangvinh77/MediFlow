package com.mediflow.lab.messaging.consumer.inbox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.lab.application.dto.command.ConsumerInboxClaim;

/** Builds semantic fingerprints from the exact bytes delivered by RabbitMQ. */
@Component
public class InboxEvidenceFactory {
    public static final int MAX_BODY_BYTES = ConsumerInboxClaim.MAX_BODY_BYTES;
    private static final Set<String> VOLATILE_ROOT_FIELDS = Set.of("eventId", "occurredAt", "correlationId");

    private final ObjectMapper objectMapper;

    public InboxEvidenceFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ConsumerInboxClaim create(String consumerName, String routingKey, UUID eventId, String operationKey,
                                     byte[] rawBody) {
        return create(consumerName, routingKey, eventId, operationKey, rawBody, List.of());
    }

    public ConsumerInboxClaim create(String consumerName, String routingKey, UUID eventId, String operationKey,
                                     byte[] rawBody, List<UUID> legacyEffectIds) {
        if (eventId == null) {
            throw new IllegalArgumentException("Inbox eventId is required");
        }
        if (rawBody == null || rawBody.length == 0 || rawBody.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Inbox raw body must be 1 byte through 1 MiB");
        }
        try (JsonParser parser = objectMapper.getFactory().createParser(rawBody)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonNode root = objectMapper.readTree(parser);
            if (root == null || !root.isObject() || parser.nextToken() != null) {
                throw new IllegalArgumentException("Inbox body must contain exactly one JSON object");
            }
            JsonNode rawEventId = root.get("eventId");
            if (rawEventId == null || !rawEventId.isTextual()
                    || !eventId.toString().equalsIgnoreCase(rawEventId.textValue())) {
                throw new IllegalArgumentException("Inbox body eventId does not match the validated envelope");
            }
            JsonNode eventType = root.get("eventType");
            if (eventType != null && (!eventType.isTextual() || !routingKey.equals(eventType.textValue()))) {
                throw new IllegalArgumentException("Inbox body eventType does not match its RabbitMQ routing key");
            }
            ObjectNode canonicalRoot = (ObjectNode) canonicalize(root, true, "");
            byte[] canonical = objectMapper.writeValueAsBytes(canonicalRoot);
            return new ConsumerInboxClaim(consumerName, routingKey, eventId, operationKey,
                    rawBody, canonical, legacyEffectIds);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Inbox body is not valid JSON", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node, boolean root, String path) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                if (root && VOLATILE_ROOT_FIELDS.contains(name)) {
                    continue;
                }
                sorted.set(name, canonicalize(node.get(name), false,
                        path.isEmpty() ? name : path + "." + name));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(value -> array.add(canonicalize(value, false, path + "[]")));
            if ("labTestIds".equals(path) || "payload.labTestIds".equals(path)) {
                List<JsonNode> sortedValues = new ArrayList<>();
                array.forEach(sortedValues::add);
                sortedValues.sort(Comparator.comparing(JsonNode::toString));
                array.removeAll();
                sortedValues.forEach(array::add);
            }
            return array;
        }
        return node.deepCopy();
    }
}
