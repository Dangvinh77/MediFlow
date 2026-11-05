package com.mediflow.pharmacy.domain.model;

import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.Getter;

/** Immutable price snapshot and requested quantity for one drug in a prescription. */
@Getter
public class PrescriptionLine {

    private static final int MAX_NOTE_LENGTH = 500;

    private final UUID lineId;
    private final UUID drugId;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final String dosage;
    private final BigDecimal lineTotal;
    private final String drugNameSnapshot;
    private final String note;

    private PrescriptionLine(
            UUID lineId,
            UUID drugId,
            int quantity,
            BigDecimal unitPrice,
            String dosage,
            BigDecimal lineTotal, String drugNameSnapshot, String note) {
        this.lineId = lineId;
        this.drugId = drugId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.dosage = dosage;
        this.lineTotal = lineTotal;
        this.drugNameSnapshot = drugNameSnapshot;
        this.note = note;
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
        return new PrescriptionLine(null, drugId, quantity, unitPrice, dosage, lineTotal, null, null);
    }

    /** Captures a server-resolved catalogue name; never supplied by a client or backfilled historically. */
    public static PrescriptionLine create(UUID drugId, int quantity, BigDecimal unitPrice, String dosage, String drugNameSnapshot) {
        return create(drugId, quantity, unitPrice, dosage, drugNameSnapshot, null);
    }

    /** Captures a server-resolved catalogue name together with the prescriber's optional note. */
    public static PrescriptionLine create(UUID drugId, int quantity, BigDecimal unitPrice, String dosage,
            String drugNameSnapshot, String note) {
        requireName(drugNameSnapshot);
        requireNote(note);
        var line = create(drugId, quantity, unitPrice, dosage);
        return new PrescriptionLine(line.lineId, drugId, quantity, unitPrice, dosage, line.lineTotal, drugNameSnapshot, note);
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
        return restore(lineId, drugId, quantity, unitPrice, dosage, lineTotal, null);
    }

    public static PrescriptionLine restore(UUID lineId, UUID drugId, int quantity, BigDecimal unitPrice,
            String dosage, BigDecimal lineTotal, String drugNameSnapshot) {
        return restore(lineId, drugId, quantity, unitPrice, dosage, lineTotal, drugNameSnapshot, null);
    }

    public static PrescriptionLine restore(UUID lineId, UUID drugId, int quantity, BigDecimal unitPrice,
            String dosage, BigDecimal lineTotal, String drugNameSnapshot, String note) {
        if (drugNameSnapshot != null) requireName(drugNameSnapshot);
        requireNote(note);
        return new PrescriptionLine(lineId, drugId, quantity, unitPrice, dosage, lineTotal, drugNameSnapshot, note);
    }

    private static void requireNote(String note) {
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new PrescriptionRuleException("PRESCRIPTION_LINE_NOTE_TOO_LONG", "A prescription line note is limited to 500 characters");
        }
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.length() > 150) {
            throw new PrescriptionRuleException("PRESCRIPTION_DRUG_NAME_REQUIRED", "A bounded drug name snapshot is required");
        }
    }
}
