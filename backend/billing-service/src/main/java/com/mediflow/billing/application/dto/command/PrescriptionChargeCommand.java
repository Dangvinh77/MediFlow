package com.mediflow.billing.application.dto.command;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Pharmacy-owned immutable server price snapshot, not a client price command. */
public record PrescriptionChargeCommand(UUID eventId, String deliveryFingerprint, String sourceFingerprint,
        String correlationId, UUID prescriptionId, UUID recordId, UUID patientId, UUID departmentId,
        String careContext, String careEpisodeType, UUID careEpisodeId, UUID admissionId,
        String priceCode, List<Item> items, BigDecimal totalAmount, Instant createdAt, String snapshotFingerprint) {
    public PrescriptionChargeCommand {
        if (eventId == null || prescriptionId == null || patientId == null || departmentId == null
                || careEpisodeId == null || createdAt == null || !fingerprint(deliveryFingerprint)
                || !fingerprint(sourceFingerprint) || !fingerprint(snapshotFingerprint) || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 120 || correlationId.chars().anyMatch(Character::isISOControl)
                || priceCode == null || priceCode.isBlank() || priceCode.length() > 64
                || !("OUTPATIENT".equals(careContext) && "OUTPATIENT_VISIT".equals(careEpisodeType) && admissionId == null
                    || "ADMISSION".equals(careContext) && "ADMISSION".equals(careEpisodeType) && careEpisodeId.equals(admissionId))
                || items == null || items.isEmpty() || items.size() > 1000
                || !money(totalAmount) || totalAmount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid prescription charge context");
        }
        items = List.copyOf(items);
        if (new HashSet<>(items.stream().map(Item::drugId).toList()).size() != items.size())
            throw new IllegalArgumentException("Duplicate prescription drug");
        BigDecimal sum = items.stream().map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!money(sum) || sum.compareTo(totalAmount) != 0)
            throw new IllegalArgumentException("Prescription total does not match server snapshot");
        totalAmount = totalAmount.setScale(2, RoundingMode.UNNECESSARY);
    }
    private static boolean fingerprint(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean money(BigDecimal amount) {
        return amount != null && amount.stripTrailingZeros().scale() <= 2 && amount.precision() - amount.scale() <= 17;
    }
    public record Item(UUID drugId, String drugName, int quantity, BigDecimal unitPrice) {
        public Item {
            if (drugId == null || drugName == null || drugName.isBlank() || drugName.length() > 255
                    || quantity <= 0 || !money(unitPrice) || unitPrice.signum() < 0)
                throw new IllegalArgumentException("Invalid prescription drug price snapshot");
            unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
        }
    }
}
