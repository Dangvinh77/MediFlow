package com.mediflow.pharmacy.domain.model;

import java.math.BigDecimal;

import java.time.Instant;
import java.time.LocalDate;

import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.DrugRuleException;

import lombok.Getter;

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

  public static Drug create(String drugName, String activeIngredient, String unit, BigDecimal price, int stockQuantity,
                            LocalDate expiryDate, String manufacturer, int lowStockThreshold
  ){
          if(drugName == null || drugName.isBlank()){
             throw new DrugRuleException("DRUG_NAME_REQUIRED", "Tên thuốc không được bỏ trống");
          }
          
          if(unit == null || unit.isBlank()){
             throw new DrugRuleException("DRUG_UNIT_REQUIRED", "Đơn vị của thuốc không được bỏ trống");
          }

          if(price.compareTo(BigDecimal.ZERO) < 0){
              throw new DrugRuleException("DRUG_PRICE_NEGATIVE", "Giá của thuốc không được âm");
          }

          if(expiryDate.isBefore(LocalDate.now())){
              throw new DrugRuleException("DRUG_EXPIRY_PAST", "Hạn sử dụng của thuốc không được ở quá khứ");
          }

          return new Drug(null, drugName, activeIngredient, unit, price, stockQuantity, expiryDate, manufacturer, lowStockThreshold, null, null);
  }
  
    /** Dựng lại từ dữ liệu đã lưu — không chạy lại quy tắc lúc tạo. */
    public static Drug restore(UUID drugId, String drugName, String activeIngredient, String unit, BigDecimal price,
                               int stockQuantity, LocalDate expiryDate, String manufacturer, int lowStockThreshold,
                               Instant createdAt, Instant updatedAt) {
        return new Drug(drugId, drugName, activeIngredient, unit, price, stockQuantity,
                expiryDate, manufacturer, lowStockThreshold, createdAt, updatedAt);
    }

    public void updateInfo(String drugName, String activeIngredient, String unit, BigDecimal price,
                           LocalDate expiryDate, String manufacturer, int lowStockThreshold) {
        if (drugName == null || drugName.isBlank())
            throw new DrugRuleException("DRUG_NAME_REQUIRED", "Tên thuốc không được để trống");
        if (unit == null || unit.isBlank())
            throw new DrugRuleException("DRUG_UNIT_REQUIRED", "Đơn vị tính không được để trống");
        if (price.compareTo(BigDecimal.ZERO) < 0)
            throw new DrugRuleException("DRUG_PRICE_NEGATIVE", "Giá thuốc không được âm");
        if (expiryDate == null || expiryDate.isBefore(LocalDate.now()))
            throw new DrugRuleException("DRUG_EXPIRY_PAST", "Hạn sử dụng của thuốc không được ở quá khứ");
        this.drugName = drugName;
        this.activeIngredient = activeIngredient;
        this.unit = unit;
        this.price = price;
        this.expiryDate = expiryDate;
        this.manufacturer = manufacturer;
        this.lowStockThreshold = lowStockThreshold;
    }

    /** Nhập kho. */
    public void restock(int quantity) {
        if (quantity <= 0)
            throw new DrugRuleException("DRUG_QUANTITY_INVALID", "Số lượng nhập phải lớn hơn 0");
        this.stockQuantity += quantity;
    }

    /** Xuất kho — ném nếu vi phạm BR-D1 (hết hàng) hoặc BR-D2 (hết hạn). */
    public void dispenseStock(int quantity) {
        if (quantity <= 0)
            throw new DrugRuleException("DRUG_QUANTITY_INVALID", "Số lượng xuất phải lớn hơn 0");
        if (stockQuantity < quantity)
            throw new DrugRuleException("DRUG_OUT_OF_STOCK", "Không đủ hàng tồn kho");
        if (isExpired())
            throw new DrugRuleException("DRUG_EXPIRED", "Thuốc đã hết hạn sử dụng");
        this.stockQuantity -= quantity;
    }

    public boolean hasStock(int quantity) {
        return stockQuantity >= quantity;
    }

    public boolean isExpired() {
        return expiryDate == null || expiryDate.isBefore(LocalDate.now());
    }

    public boolean belowLowStockThreshold() {
        return stockQuantity <= lowStockThreshold;
    }

}
