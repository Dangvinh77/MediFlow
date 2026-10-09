package com.mediflow.billing.application.dto.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Exact immutable creation fact; no producer-supplied money or inferred episode identifiers. */
public record SurgeryChargeCommand(UUID eventId, String deliveryFingerprint, String sourceFingerprint,
        String correlationId, UUID surgeryCaseId, UUID surgeryRequestId, UUID patientId, UUID departmentId,
        String careEpisodeType, UUID careEpisodeId, UUID admissionId, UUID recordId,
        UUID requestedBy, Instant requestedAt, List<Item> plannedItems) {
    public SurgeryChargeCommand {
        if (eventId == null || surgeryCaseId == null || surgeryRequestId == null || patientId == null
                || departmentId == null || careEpisodeId == null || requestedBy == null || requestedAt == null
                || !fingerprint(deliveryFingerprint) || !fingerprint(sourceFingerprint)
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || !("ADMISSION".equals(careEpisodeType) ? careEpisodeId.equals(admissionId)
                    : "OUTPATIENT_VISIT".equals(careEpisodeType) && admissionId == null)
                || plannedItems == null || plannedItems.isEmpty() || plannedItems.size() > 1000)
            throw new IllegalArgumentException("Invalid Surgery charge context");
        plannedItems = List.copyOf(plannedItems);
        if (new HashSet<>(plannedItems.stream().map(Item::itemCode).toList()).size() != plannedItems.size())
            throw new IllegalArgumentException("Duplicate Surgery planned item");
    }
    private static boolean fingerprint(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    public record Item(String itemCode, String priceCode, BigDecimal quantity) {
        public Item {
            if (itemCode == null || !itemCode.matches("[A-Za-z0-9._-]{1,64}")
                    || priceCode == null || !priceCode.matches("[A-Za-z0-9._-]{1,64}")
                    || quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 4
                    || quantity.precision() - quantity.scale() > 15)
                throw new IllegalArgumentException("Invalid Surgery planned item");
            quantity = quantity.stripTrailingZeros();
        }
    }
}
