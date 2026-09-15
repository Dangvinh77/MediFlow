package com.mediflow.pharmacy.domain.model;

import com.mediflow.pharmacy.domain.exception.DrugRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/** Aggregate containing drug catalogue data and stock invariants. */
@Getter
public class Drug {

    private final UUID drugId;
    private String drugName;
    private String activeIngredient;
    private String unit;
    private BigDecimal price;
    private int stockQuantity;
    private LocalDate expiryDate;
    private String manufacturer;
    private int lowStockThreshold;
    private final Instant createdAt;
    private Instant updatedAt;

    private Drug(
            UUID drugId,
            String drugName,
            String activeIngredient,
            String unit,
            BigDecimal price,
            int stockQuantity,
            LocalDate expiryDate,
            String manufacturer,
            int lowStockThreshold,
            Instant createdAt,
            Instant updatedAt) {
        this.drugId = drugId;
        this.drugName = drugName;
        this.activeIngredient = activeIngredient;
        this.unit = unit;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.expiryDate = expiryDate;
        this.manufacturer = manufacturer;
        this.lowStockThreshold = lowStockThreshold;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates a drug using the machine's current date as the hospital business date.
     *
     * @param drugName display name
     * @param activeIngredient active ingredient
     * @param unit dispensing unit
     * @param price non-negative unit price
     * @param stockQuantity initial physical stock
     * @param expiryDate inclusive final usable date
     * @param manufacturer manufacturer name
     * @param lowStockThreshold non-negative warning threshold
     * @return validated drug aggregate
     */
    public static Drug create(
            String drugName,
            String activeIngredient,
            String unit,
            BigDecimal price,
            int stockQuantity,
            LocalDate expiryDate,
            String manufacturer,
            int lowStockThreshold) {
        return create(
                drugName,
                activeIngredient,
                unit,
                price,
                stockQuantity,
                expiryDate,
                manufacturer,
                lowStockThreshold,
                LocalDate.now());
    }

    /**
     * Creates a drug using an explicit hospital business date for deterministic validation.
     *
     * @param drugName display name
     * @param activeIngredient active ingredient
     * @param unit dispensing unit
     * @param price non-negative unit price
     * @param stockQuantity initial physical stock
     * @param expiryDate inclusive final usable date
     * @param manufacturer manufacturer name
     * @param lowStockThreshold non-negative warning threshold
     * @param businessDate date against which expiry is validated
     * @return validated drug aggregate
     */
    public static Drug create(
            String drugName,
            String activeIngredient,
            String unit,
            BigDecimal price,
            int stockQuantity,
            LocalDate expiryDate,
            String manufacturer,
            int lowStockThreshold,
            LocalDate businessDate) {
        validateCatalogue(drugName, unit, price, expiryDate, lowStockThreshold, businessDate);
        if (stockQuantity < 0) {
            throw new DrugRuleException(
                    "DRUG_QUANTITY_INVALID", "Số lượng tồn kho không được âm");
        }
        return new Drug(
                null,
                drugName,
                activeIngredient,
                unit,
                price,
                stockQuantity,
                expiryDate,
                manufacturer,
                lowStockThreshold,
                null,
                null);
    }

    /**
     * Restores a previously validated drug from persistence.
     *
     * @param drugId persistent identity
     * @param drugName display name
     * @param activeIngredient active ingredient
     * @param unit dispensing unit
     * @param price unit price
     * @param stockQuantity physical stock
     * @param expiryDate inclusive final usable date
     * @param manufacturer manufacturer name
     * @param lowStockThreshold warning threshold
     * @param createdAt creation timestamp
     * @param updatedAt most recent mutation timestamp
     * @return rehydrated aggregate
     */
    public static Drug restore(
            UUID drugId,
            String drugName,
            String activeIngredient,
            String unit,
            BigDecimal price,
            int stockQuantity,
            LocalDate expiryDate,
            String manufacturer,
            int lowStockThreshold,
            Instant createdAt,
            Instant updatedAt) {
        return new Drug(
                drugId,
                drugName,
                activeIngredient,
                unit,
                price,
                stockQuantity,
                expiryDate,
                manufacturer,
                lowStockThreshold,
                createdAt,
                updatedAt);
    }

    /**
     * Updates catalogue information while retaining stock and using the current business date.
     *
     * @param drugName display name
     * @param activeIngredient active ingredient
     * @param unit dispensing unit
     * @param price non-negative unit price
     * @param expiryDate inclusive final usable date
     * @param manufacturer manufacturer name
     * @param lowStockThreshold non-negative warning threshold
     */
    public void updateInfo(
            String drugName,
            String activeIngredient,
            String unit,
            BigDecimal price,
            LocalDate expiryDate,
            String manufacturer,
            int lowStockThreshold) {
        updateInfo(
                drugName,
                activeIngredient,
                unit,
                price,
                expiryDate,
                manufacturer,
                lowStockThreshold,
                LocalDate.now());
    }

    /**
     * Updates catalogue information using an explicit hospital business date.
     *
     * @param drugName display name
     * @param activeIngredient active ingredient
     * @param unit dispensing unit
     * @param price non-negative unit price
     * @param expiryDate inclusive final usable date
     * @param manufacturer manufacturer name
     * @param lowStockThreshold non-negative warning threshold
     * @param businessDate date against which expiry is validated
     */
    public void updateInfo(
            String drugName,
            String activeIngredient,
            String unit,
            BigDecimal price,
            LocalDate expiryDate,
            String manufacturer,
            int lowStockThreshold,
            LocalDate businessDate) {
        validateCatalogue(drugName, unit, price, expiryDate, lowStockThreshold, businessDate);
        this.drugName = drugName;
        this.activeIngredient = activeIngredient;
        this.unit = unit;
        this.price = price;
        this.expiryDate = expiryDate;
        this.manufacturer = manufacturer;
        this.lowStockThreshold = lowStockThreshold;
        this.updatedAt = Instant.now();
    }

    /**
     * Adds a positive amount of stock without allowing integer overflow.
     *
     * @param quantity quantity received into inventory
     */
    public void restock(int quantity) {
        if (quantity <= 0) {
            throw new DrugRuleException(
                    "DRUG_QUANTITY_INVALID", "Số lượng nhập phải lớn hơn 0");
        }
        long newStock = (long) stockQuantity + quantity;
        if (newStock > Integer.MAX_VALUE) {
            throw new DrugRuleException(
                    "DRUG_QUANTITY_INVALID", "Số lượng tồn kho vượt giới hạn");
        }
        stockQuantity = (int) newStock;
    }

    /**
     * Applies an audited manual stock delta without allowing negative stock or overflow.
     *
     * @param quantity non-zero stock delta
     * @param updatedAt audited mutation timestamp supplied by the application clock
     */
    public void adjustStock(int quantity, Instant updatedAt) {
        if (quantity == 0) {
            throw new DrugRuleException(
                    "DRUG_QUANTITY_INVALID", "Số lượng điều chỉnh không được bằng 0");
        }
        long newStock = (long) stockQuantity + quantity;
        if (newStock < 0) {
            throw new DrugRuleException("DRUG_OUT_OF_STOCK", "Điều chỉnh làm tồn kho âm");
        }
        if (newStock > Integer.MAX_VALUE) {
            throw new DrugRuleException(
                    "DRUG_QUANTITY_INVALID", "Số lượng tồn kho vượt giới hạn");
        }
        if (updatedAt == null) {
            throw new DrugRuleException(
                    "DRUG_UPDATE_TIME_REQUIRED", "Thời điểm cập nhật thuốc là bắt buộc");
        }
        stockQuantity = (int) newStock;
        this.updatedAt = updatedAt;
    }

    /**
     * Dispenses stock using the machine's current date as the hospital business date.
     *
     * @param quantity requested dispensing quantity
     */
    public void dispenseStock(int quantity) {
        dispenseStock(quantity, LocalDate.now());
    }

    /**
     * Dispenses stock while enforcing BR-D1 availability and BR-D2 expiry boundaries.
     *
     * @param quantity requested dispensing quantity
     * @param businessDate hospital date used for expiry validation
     */
    public void dispenseStock(int quantity, LocalDate businessDate) {
        if (quantity <= 0) {
            throw new DrugRuleException(
                    "DRUG_QUANTITY_INVALID", "Số lượng xuất phải lớn hơn 0");
        }
        if (stockQuantity < quantity) {
            throw new DrugRuleException("DRUG_OUT_OF_STOCK", "Không đủ hàng tồn kho");
        }
        if (businessDate == null || isExpiredOn(businessDate)) {
            throw new DrugRuleException("DRUG_EXPIRED", "Thuốc đã hết hạn sử dụng");
        }
        stockQuantity -= quantity;
    }

    /**
     * Reports whether physical stock can satisfy a positive requested quantity.
     *
     * @param quantity requested quantity
     * @return {@code true} when enough physical stock exists
     */
    public boolean hasStock(int quantity) {
        return quantity > 0 && stockQuantity >= quantity;
    }

    /** Returns whether the drug is expired on the machine's current date. */
    public boolean isExpired() {
        return isExpiredOn(LocalDate.now());
    }

    /**
     * Reports whether expiry is before the supplied inclusive business-date boundary.
     *
     * @param businessDate hospital business date
     * @return {@code true} when the drug cannot be dispensed on that date
     */
    public boolean isExpiredOn(LocalDate businessDate) {
        return businessDate == null || expiryDate == null || expiryDate.isBefore(businessDate);
    }

    /** Returns whether physical stock is at or below the configured warning threshold. */
    public boolean belowLowStockThreshold() {
        return stockQuantity <= lowStockThreshold;
    }

    private static void validateCatalogue(
            String drugName,
            String unit,
            BigDecimal price,
            LocalDate expiryDate,
            int lowStockThreshold,
            LocalDate businessDate) {
        if (drugName == null || drugName.isBlank()) {
            throw new DrugRuleException("DRUG_NAME_REQUIRED", "Tên thuốc không được bỏ trống");
        }
        if (unit == null || unit.isBlank()) {
            throw new DrugRuleException("DRUG_UNIT_REQUIRED", "Đơn vị của thuốc không được bỏ trống");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new DrugRuleException("DRUG_PRICE_NEGATIVE", "Giá của thuốc không được âm");
        }
        if (expiryDate == null || businessDate == null || expiryDate.isBefore(businessDate)) {
            throw new DrugRuleException(
                    "DRUG_EXPIRY_PAST", "Hạn sử dụng của thuốc không được ở quá khứ");
        }
        if (lowStockThreshold < 0) {
            throw new DrugRuleException(
                    "DRUG_QUANTITY_INVALID", "Ngưỡng cảnh báo tồn kho không được âm");
        }
    }
}
