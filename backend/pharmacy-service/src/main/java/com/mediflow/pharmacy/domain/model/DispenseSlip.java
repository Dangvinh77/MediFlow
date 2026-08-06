package com.mediflow.pharmacy.domain.model;


import java.time.Instant;
import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.DispenseRuleException;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;

import lombok.Getter;

//Phiếu xuất thuốc
@Getter
public class DispenseSlip {
   private final UUID dispenseId;
   private final UUID prescriptionId;
   private DispenseStatus status;
   private Instant dispenseAt;
   private UUID dispenseBy;
   private String failureReason;
   private final Instant createdAt;
   private Instant updatedAt;

   private DispenseSlip(UUID dispenseId, UUID prescriptionId, DispenseStatus status, Instant dispenseAt, UUID dispenseBy,
                        String failureReason, Instant createdAt, Instant updatedAt
   ){
          this.dispenseId = dispenseId;
          this.prescriptionId = prescriptionId;
          this.status = status;
          this.dispenseAt = dispenseAt;
          this.dispenseBy = dispenseBy;
          this.failureReason = failureReason;
          this.createdAt = createdAt;
          this.updatedAt = updatedAt;
   }

    //tạo đơn thuốc luôn sinh phiếu PENDING
    public static DispenseSlip createPending(UUID prescriptionId){
        return new DispenseSlip(null, prescriptionId, DispenseStatus.PENDING, null, null, null, null, null);
    }

      public static DispenseSlip restore(UUID dispenseId, UUID prescriptionId, DispenseStatus status,
                                       Instant dispensedAt, UUID dispensedBy, String failureReason,
                                       Instant createdAt, Instant updatedAt) {
        return new DispenseSlip(dispenseId, prescriptionId, status, dispensedAt, dispensedBy,
                failureReason, createdAt, updatedAt);
    }

    //PENDING -> DISPENSED
    public void markDispensed(UUID dispensedBy, Instant timestamp) {
        if (status == DispenseStatus.DISPENSED)
            throw new DispenseRuleException("DISPENSE_ALREADY_DONE", "Phiếu xuất đã được xuất rồi");
        if (status == DispenseStatus.FAILED)
            throw new DispenseRuleException("DISPENSE_INVALID_TRANSITION", "Không thể xuất phiếu đã thất bại");
        this.status = DispenseStatus.DISPENSED;
        this.dispenseBy = dispensedBy;
        this.dispenseAt = timestamp;
    }

    /** PENDING → FAILED (nhánh bù trừ saga, BR-D12). */
    public void markFailed(String reason) {
        if (status != DispenseStatus.PENDING)
            throw new DispenseRuleException("DISPENSE_INVALID_TRANSITION", "Phiếu không còn ở trạng thái chờ xuất");
        this.status = DispenseStatus.FAILED;
        this.failureReason = reason;
    }

    public boolean isPending() {
        return status == DispenseStatus.PENDING;
    }


}
