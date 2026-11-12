package com.mediflow.pharmacy.application.dto.command;

import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Immutable, server-priced snapshot shared with Billing while revising a prescription source. */
public record PrescriptionRevisionBillingPayload(
        UUID prescriptionId, UUID recordId, UUID admissionId, UUID patientId, UUID departmentId,
        CareContext careContext, CareEpisodeType careEpisodeType, UUID careEpisodeId,
        String sourceType, UUID sourceId, String priceCode,
        List<Item> items, BigDecimal totalAmount,
        int expectedRevision, int revision) {
    public PrescriptionRevisionBillingPayload {
        if (prescriptionId == null || recordId == null || patientId == null || departmentId == null
                || careContext != CareContext.OUTPATIENT || careEpisodeType != CareEpisodeType.OUTPATIENT_VISIT
                || careEpisodeId == null || sourceId == null || !prescriptionId.equals(sourceId)
                || !"PRESCRIPTION".equals(sourceType) || priceCode == null || priceCode.isBlank()
                || items == null || items.isEmpty() || totalAmount == null || totalAmount.signum() < 0
                || expectedRevision < 0 || revision != expectedRevision + 1) {
            throw new IllegalArgumentException("A complete outpatient V1 revision snapshot is required");
        }
        items = List.copyOf(items);
        totalAmount = totalAmount.setScale(2, java.math.RoundingMode.UNNECESSARY);
        BigDecimal itemTotal = items.stream().map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, java.math.RoundingMode.UNNECESSARY);
        if (itemTotal.compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException("Revision amount must match its exact priced items");
        }
    }

    public record Item(UUID drugId, String drugName, int quantity, BigDecimal unitPrice) {
        public Item {
            if (drugId == null || drugName == null || drugName.isBlank() || quantity < 1
                    || unitPrice == null || unitPrice.signum() < 0) {
                throw new IllegalArgumentException("Invalid revision item");
            }
            unitPrice = unitPrice.setScale(2, java.math.RoundingMode.UNNECESSARY);
        }
    }
}
