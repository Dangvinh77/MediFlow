package com.mediflow.pharmacy.domain.model;

import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.Getter;

/** Immutable price snapshot and requested quantity for one drug in a prescription. */
@Getter
public class PrescriptionLine {

    private final UUID lineId;
    private final UUID drugId;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final String dosage;
    private final BigDecimal lineTotal;

    private PrescriptionLine(
            UUID lineId,
            UUID drugId,
            int quantity,
            BigDecimal unitPrice,
            String dosage,
            BigDecimal lineTotal) {
        this.lineId = lineId;
        this.drugId = drugId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.dosage = dosage;
        this.lineTotal = lineTotal;
    }

    /**
     * Creates a line and calculates its immutable server-side monetary snapshot.
     *
     * @param drugId prescribed drug identity
     * @param quantity strictly positive quantity
     * @param unitPrice catalogue price resolved by the server
     * @param dosage human-readable dosage instructions
     * @return validated prescription line with a two-decimal total
     */
    public static PrescriptionLine create(
            UUID drugId, int quantity, BigDecimal unitPrice, String dosage) {
        if (quantity <= 0) {
            throw new PrescriptionRuleException(
                    "DRUG_QUANTITY_INVALID", "Số lượng phải lớn hơn 0");
        }
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
        return new PrescriptionLine(null, drugId, quantity, unitPrice, dosage, lineTotal);
    }

    /**
     * Restores an immutable line from persistence without recalculating its historical price.
     *
     * @param lineId persistent line identity
     * @param drugId prescribed drug identity
     * @param quantity prescribed quantity
     * @param unitPrice historical unit-price snapshot
     * @param dosage dosage instructions
     * @param lineTotal historical line total
     * @return rehydrated prescription line
     */
    public static PrescriptionLine restore(
            UUID lineId,
            UUID drugId,
            int quantity,
            BigDecimal unitPrice,
            String dosage,
            BigDecimal lineTotal) {
        return new PrescriptionLine(lineId, drugId, quantity, unitPrice, dosage, lineTotal);
    }
}
