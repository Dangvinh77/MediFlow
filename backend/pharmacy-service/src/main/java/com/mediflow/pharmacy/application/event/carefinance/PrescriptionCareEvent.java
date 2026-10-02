package com.mediflow.pharmacy.application.event.carefinance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.mediflow.pharmacy.domain.model.CareEpisode;
import com.mediflow.pharmacy.domain.model.PrescriptionCareContext;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;

/** Proposed V1 producer DTOs, separate from the committed flat V0 outbox contract. */
public record PrescriptionCareEvent(UUID eventId, EventType eventType, int version, Instant occurredAt,
        String correlationId, String producer, Payload payload) {

    public enum EventType {
        CREATED("prescription.created"), FILLED("prescription.filled"),
        DISPENSE_FAILED("prescription.dispense.failed"), CANCELLED("prescription.cancelled"),
        EXPIRED("prescription.expired");

        private final String routingKey;
        EventType(String routingKey) { this.routingKey = routingKey; }
        @JsonValue public String routingKey() { return routingKey; }
        @JsonCreator public static EventType fromRoutingKey(String value) {
            for (var type : values()) {
                if (type.routingKey.equals(value)) return type;
            }
            throw new IllegalArgumentException("Unsupported prescription event type");
        }
    }

    public PrescriptionCareEvent {
        if (eventId == null || eventType == null || version != 1 || occurredAt == null
                || correlationId == null || correlationId.isBlank()
                || !"pharmacy-service".equals(producer) || payload == null) {
            throw new IllegalArgumentException("Complete Pharmacy V1 envelope is required");
        }
        Instant businessAt = switch (eventType) {
            case CREATED -> payload.createdAt();
            case FILLED -> payload.filledAt();
            case DISPENSE_FAILED -> payload.failedAt();
            case CANCELLED -> payload.cancelledAt();
            case EXPIRED -> payload.expiredAt();
        };
        long timestamps = java.util.stream.Stream.of(payload.createdAt(), payload.filledAt(),
                payload.failedAt(), payload.cancelledAt(), payload.expiredAt()).filter(java.util.Objects::nonNull).count();
        if (businessAt == null || timestamps != 1 || !businessAt.equals(occurredAt)
                || (eventType == EventType.FILLED) != (payload.dispenseId() != null)) {
            throw new IllegalArgumentException("Event type requires its exact business timestamp and dispense identity");
        }
        boolean needsReason = eventType == EventType.DISPENSE_FAILED || eventType == EventType.CANCELLED
                || eventType == EventType.EXPIRED;
        if (needsReason != (payload.reason() != null)) {
            throw new IllegalArgumentException("Only failed/cancelled/expired events require a reason");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Payload(UUID prescriptionId, UUID recordId, UUID admissionId, UUID patientId, UUID departmentId,
            CareContext careContext, CareEpisodeType careEpisodeType, UUID careEpisodeId,
            String sourceType, UUID sourceId, String priceCode, List<Item> items, BigDecimal totalAmount,
            UUID dispenseId, Instant createdAt, Instant filledAt, Instant failedAt, Instant cancelledAt,
            Instant expiredAt, String reason) {
        public Payload {
            PrescriptionCareContext.v1(careContext, new CareEpisode(careEpisodeType, careEpisodeId), admissionId, priceCode);
            if (prescriptionId == null || patientId == null || departmentId == null
                    || !"PRESCRIPTION".equals(sourceType) || !prescriptionId.equals(sourceId)
                    || items == null || items.isEmpty() || totalAmount == null || totalAmount.signum() < 0
                    || (reason != null && (reason.isBlank() || reason.length() > 500))) {
                throw new IllegalArgumentException("Incomplete exact prescription event snapshot");
            }
            items = List.copyOf(items);
            var seen = new HashSet<UUID>();
            BigDecimal sum = BigDecimal.ZERO;
            for (var item : items) {
                if (!seen.add(item.drugId())) throw new IllegalArgumentException("Duplicate prescription item");
                sum = sum.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
            }
            totalAmount = totalAmount.setScale(2, RoundingMode.UNNECESSARY);
            if (sum.compareTo(totalAmount) != 0) {
                throw new IllegalArgumentException("Prescription total must match its immutable priced item snapshot");
            }
        }
    }

    public record Item(UUID drugId, String drugName, int quantity, BigDecimal unitPrice) {
        public Item {
            if (drugId == null || drugName == null || drugName.isBlank() || quantity <= 0
                    || unitPrice == null || unitPrice.signum() < 0) {
                throw new IllegalArgumentException("Invalid priced prescription item");
            }
            unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
        }
    }
}
