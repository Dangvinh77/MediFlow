package com.mediflow.pharmacy.domain.model;

import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.DrugRulesException;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.Getter;

@Getter
public class PrescriptionLine {
  private final UUID lineId;
  private final UUID drugId;
  private final int quantity;
  private final BigDecimal unitPrice;
  private final String dosage;
  private final BigDecimal lineTotal;

  private PrescriptionLine(UUID lineId, UUID drugId, int quantity, BigDecimal unitPrice,
                          String dosage, BigDecimal lineTotal)
                          {
       this.lineId = lineId;
       this.drugId = drugId;
       this.quantity = quantity;
       this.unitPrice = unitPrice;
       this.dosage = dosage;
       this.lineTotal = lineTotal;
  }

  public static PrescriptionLine create(UUID drugId, int quantity, BigDecimal unitPrice, String dosage){
       if(quantity <= 0){
        throw new DrugRulesException("DRUG_QUANTITY_INVALID", "Số lượng phải lớn hơn 0");
       }

       BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity))
                              .setScale(2, RoundingMode.HALF_UP);
    
        return new PrescriptionLine(null, drugId, quantity, unitPrice, dosage, lineTotal);
  }

  public static PrescriptionLine restore(UUID lineId, UUID drugId, int quantity, BigDecimal unitPrice,
                          String dosage, BigDecimal lineTotal){
        return new PrescriptionLine(lineId, drugId, quantity, unitPrice, dosage, lineTotal);
  }

}
