package com.mediflow.pharmacy.domain.model;

import java.math.BigDecimal;

import java.time.Instant;
import java.time.LocalDate;

import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.DrugRuleException;

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
 private String manufacturer; //nhà sản xuất
 private int lowStockThreshold; //mức độ tồn kho của hàng hóa
 private final Instant createdAt;
 private Instant updatedAt;

  private Drug(UUID drugId, String drugName, String activeIngredient, String unit, BigDecimal price ,int stockQuantity, LocalDate expiryDate,
               String manufacturer, int lowStockThreshold, Instant createdAt, Instant updatedAt
  ){
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

  /** Creates a drug after validating catalogue and stock invariants. */
  public static Drug create(String drugName, String activeIngredient, String unit, BigDecimal price, int stockQuantity,
                            LocalDate expiryDate, String manufacturer, int lowStockThreshold
  ){
          if(drugName == null || drugName.isBlank()){
             throw new DrugRuleException("DRUG_NAME_REQUIRED", "Tên thuốc không được bỏ trống");
          }
          
          if(unit == null || unit.isBlank()){
             throw new DrugRuleException("DRUG_UNIT_REQUIRED", "Đơn vị của thuốc không được bỏ trống");
          }

          if(price == null || price.compareTo(BigDecimal.ZERO) < 0){
              throw new DrugRuleException("DRUG_PRICE_NEGATIVE", "Giá của thuốc không được âm");
          }

          if(expiryDate == null || expiryDate.isBefore(LocalDate.now())){
              throw new DrugRuleException("DRUG_EXPIRY_PAST", "Hạn sử dụng của thuốc không được ở quá khứ");
          }

          if (stockQuantity < 0) {
              throw new DrugRuleException("DRUG_QUANTITY_INVALID", "Số lượng tồn kho không được âm");
          }

          if (lowStockThreshold < 0) {
              throw new DrugRuleException("DRUG_QUANTITY_INVALID", "Ngưỡng cảnh báo tồn kho không được âm");
          }

          return new Drug(null, drugName, activeIngredient, unit, price, stockQuantity, expiryDate, manufacturer, lowStockThreshold, null, null);
  }
  
    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc tạo. */
    /** Restores a drug from persistence without re-running creation validation. */
    public static Drug restore(UUID drugId, String drugName, String activeIngredient, String unit, BigDecimal price,
                               int stockQuantity, LocalDate expiryDate, String manufacturer, int lowStockThreshold,
                               Instant createdAt, Instant updatedAt) {
        return new Drug(drugId, drugName, activeIngredient, unit, price, stockQuantity,
                expiryDate, manufacturer, lowStockThreshold, createdAt, updatedAt);
    }

    /** Updates catalogue information while preserving the current stock quantity. */
    public void updateInfo(String drugName, String activeIngredient, String unit, BigDecimal price,
                           LocalDate expiryDate, String manufacturer, int lowStockThreshold) {
        if (drugName == null || drugName.isBlank())
            throw new DrugRuleException("DRUG_NAME_REQUIRED", "Tên thuốc không được để trống");
        if (unit == null || unit.isBlank())
            throw new DrugRuleException("DRUG_UNIT_REQUIRED", "Đơn vị tính không được để trống");
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0)
            throw new DrugRuleException("DRUG_PRICE_NEGATIVE", "Giá thuốc không được âm");
        if (expiryDate == null || expiryDate.isBefore(LocalDate.now()))
            throw new DrugRuleException("DRUG_EXPIRY_PAST", "Hạn sử dụng của thuốc không được ở quá khứ");
        if (lowStockThreshold < 0)
            throw new DrugRuleException("DRUG_QUANTITY_INVALID", "Ngưỡng cảnh báo tồn kho không được âm");
        this.drugName = drugName;
        this.activeIngredient = activeIngredient;
        this.unit = unit;
        this.price = price;
        this.expiryDate = expiryDate;
        this.manufacturer = manufacturer;
        this.lowStockThreshold = lowStockThreshold;
        this.updatedAt = Instant.now();
    }

    /** Nhập kho. */
    public void restock(int quantity) {
        if (quantity <= 0)
            throw new DrugRuleException("DRUG_QUANTITY_INVALID", "Số lượng nhập phải lớn hơn 0");
        long newStock = (long) this.stockQuantity + quantity;
        if (newStock > Integer.MAX_VALUE) {
            throw new DrugRuleException("DRUG_QUANTITY_INVALID", "Số lượng tồn kho vượt giới hạn");
        }
        this.stockQuantity = (int) newStock;
    }

    /** Điều chỉnh tồn kho thủ công; không cho phép tồn kho âm. */
    public void adjustStock(int quantity) {
        if (quantity == 0) {
            throw new DrugRuleException("DRUG_QUANTITY_INVALID", "Số lượng điều chỉnh không được bằng 0");
        }
        long newStock = (long) stockQuantity + quantity;
        if (newStock < 0) {
            throw new DrugRuleException("DRUG_OUT_OF_STOCK", "Điều chỉnh làm tồn kho âm");
        }
        if (newStock > Integer.MAX_VALUE) {
            throw new DrugRuleException("DRUG_QUANTITY_INVALID", "Số lượng tồn kho vượt giới hạn");
        }
        this.stockQuantity = (int) newStock;
    }

    /** Xuất kho theo ngày hệ thống — ném nếu vi phạm BR-D1 hoặc BR-D2. */
    public void dispenseStock(int quantity) {
        dispenseStock(quantity, LocalDate.now());
    }

    /**
     * Xuất kho theo ngày nghiệp vụ do application cung cấp để test không phụ thuộc đồng hồ máy.
     *
     * @param quantity số lượng xuất
     * @param businessDate ngày nghiệp vụ dùng để kiểm tra hạn thuốc
     */
    public void dispenseStock(int quantity, LocalDate businessDate) {
        if (quantity <= 0)
            throw new DrugRuleException("DRUG_QUANTITY_INVALID", "Số lượng xuất phải lớn hơn 0");
        if (stockQuantity < quantity)
            throw new DrugRuleException("DRUG_OUT_OF_STOCK", "Không đủ hàng tồn kho");
        if (businessDate == null || isExpiredOn(businessDate))
            throw new DrugRuleException("DRUG_EXPIRED", "Thuốc đã hết hạn sử dụng");
        this.stockQuantity -= quantity;
    }

    /** Returns whether the current stock can satisfy the requested quantity. */
    public boolean hasStock(int quantity) {
        return stockQuantity >= quantity;
    }

    /** Returns whether the drug is expired according to the machine's current date. */
    public boolean isExpired() {
        return isExpiredOn(LocalDate.now());
    }

    /** Kiểm tra hạn thuốc theo ngày nghiệp vụ được inject từ application. */
    public boolean isExpiredOn(LocalDate businessDate) {
        return businessDate == null || expiryDate == null || expiryDate.isBefore(businessDate);
    }

    /** Returns whether stock is at or below the configured warning threshold. */
    public boolean belowLowStockThreshold() {
        return stockQuantity <= lowStockThreshold;
    }

}
