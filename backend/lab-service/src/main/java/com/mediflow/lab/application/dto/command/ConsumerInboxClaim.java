package com.mediflow.lab.application.dto.command;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Exact delivery evidence needed to atomically claim a broker event and its business operation. */
public final class ConsumerInboxClaim {
    public static final int MAX_BODY_BYTES = 1_048_576;
    private final String consumerName;
    private final String routingKey;
    private final UUID eventId;
    private final String operationKey;
    private final byte[] rawBody;
    private final byte[] canonicalBusinessBytes;
    private final List<UUID> legacyEffectIds;

    public ConsumerInboxClaim(String consumerName, String routingKey, UUID eventId, String operationKey,
                              byte[] rawBody, byte[] canonicalBusinessBytes, List<UUID> legacyEffectIds) {
        if (consumerName == null || !consumerName.matches("[a-z0-9][a-z0-9.-]{0,63}")) {
            throw new IllegalArgumentException("Inbox consumer name is invalid");
        }
        if (routingKey == null || !routingKey.matches("[a-z0-9][a-z0-9.-]{0,99}")) {
            throw new IllegalArgumentException("Inbox routing key is invalid");
        }
        this.eventId = Objects.requireNonNull(eventId, "Inbox eventId is required");
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 512) {
            throw new IllegalArgumentException("Inbox operation key is invalid");
        }
        this.consumerName = consumerName;
        this.routingKey = routingKey;
        this.operationKey = operationKey;
        this.rawBody = copyBody(rawBody, "raw");
        this.canonicalBusinessBytes = copyBody(canonicalBusinessBytes, "canonical");
        if (legacyEffectIds == null || legacyEffectIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Inbox legacy effect identifiers are invalid");
        }
        this.legacyEffectIds = List.copyOf(legacyEffectIds);
    }

    private static byte[] copyBody(byte[] body, String kind) {
        if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Inbox " + kind + " body must be 1 byte through 1 MiB");
        }
        return body.clone();
    }

    public String consumerName() { return consumerName; }
    public String routingKey() { return routingKey; }
    public UUID eventId() { return eventId; }
    public String operationKey() { return operationKey; }
    public byte[] rawBody() { return rawBody.clone(); }
    public byte[] canonicalBusinessBytes() { return canonicalBusinessBytes.clone(); }
    public List<UUID> legacyEffectIds() { return legacyEffectIds; }
}
