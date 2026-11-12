package com.mediflow.billing.application.dto.command;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Exact Pharmacy-owned V1 outpatient snapshot for one unpaid prescription revision. */
public record PrescriptionRevisionCommand(UUID prescriptionId, UUID operationId, UUID eventId,
        long expectedRevision, long revision, UUID patientId, UUID recordId, UUID departmentId,
        String careEpisodeType, UUID careEpisodeId, String priceCode, BigDecimal totalAmount,
        List<Item> items, Instant revisedAt) {
    public PrescriptionRevisionCommand {
        if (prescriptionId == null || operationId == null || patientId == null || departmentId == null
                || careEpisodeId == null || expectedRevision < 0 || expectedRevision == Long.MAX_VALUE
                || revision <= 0 || revision != expectedRevision + 1
                || !"OUTPATIENT_VISIT".equals(careEpisodeType) || priceCode == null || priceCode.isBlank()
                || priceCode.length() > 64 || priceCode.chars().anyMatch(Character::isISOControl)
                || !money(totalAmount) || totalAmount.signum() <= 0 || items == null || items.isEmpty()
                || items.size() > 1000) {
            throw new IllegalArgumentException("Invalid V1 outpatient prescription revision");
        }
        items = List.copyOf(items);
        if (new HashSet<>(items.stream().map(Item::drugId).toList()).size() != items.size()) {
            throw new IllegalArgumentException("Duplicate prescription drug");
        }
        BigDecimal sum = items.stream().map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!money(sum) || sum.compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException("Prescription total does not match its priced item snapshot");
        }
        totalAmount = totalAmount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static boolean money(BigDecimal amount) {
        return amount != null && amount.stripTrailingZeros().scale() <= 2
                && amount.precision() - amount.scale() <= 17;
    }

    public record Item(UUID drugId, String drugName, int quantity, BigDecimal unitPrice) {
        public Item {
            if (drugId == null || drugName == null || drugName.isBlank() || drugName.length() > 255
                    || drugName.chars().anyMatch(Character::isISOControl) || quantity <= 0
                    || !money(unitPrice) || unitPrice.signum() < 0) {
                throw new IllegalArgumentException("Invalid priced prescription item");
            }
            unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
        }
    }
}
