package com.mediflow.pharmacy.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;

import lombok.Getter;

@Getter
public class Prescription {
  private final UUID prescriptionId;
  private final UUID recordId;
  private final UUID patientId;
  private final UUID doctorId;
  private final UUID departmentId;
  private final LocalDate prescribedDate; // ngày bác sĩ kê đơn
  private final BigDecimal totalAmount;
  private final List<PrescriptionLine> lines;
  private final Instant createdAt;

  private Prescription(UUID prescriptionId, UUID recordId, UUID patientId, UUID doctorId,
                       UUID departmentId, LocalDate prescribedDate, BigDecimal totalAmount,
                       List<PrescriptionLine> lines, Instant createdAt
                    ){
         this.prescriptionId = prescriptionId;
         this.recordId = recordId;
         this.patientId = patientId;
         this.doctorId = doctorId;
        this.departmentId = departmentId;
        this.prescribedDate = prescribedDate;
        this.totalAmount = totalAmount;
        this.lines = lines;
        this.createdAt = createdAt;
  }

   public static Prescription create(UUID recordId, UUID patientId, UUID doctorId,
                                     UUID departmentId, LocalDate prescribedDate, List<PrescriptionLine> lines
   ){
            if(lines == null || lines.isEmpty()){
                  throw new PrescriptionRuleException("PRESCRIPTION_EMPTY", "Đơn thuốc phải có ít nhất 1 dòng");
            }
            validateUniqueDrugs(lines);
            BigDecimal total = computeTotalFrom(lines);
            return new Prescription(null, recordId, patientId, doctorId, departmentId, prescribedDate, total,List.copyOf(lines), null);
   }

    /** Dựng lại từ dữ liệu đã lưu. */
    public static Prescription restore(UUID prescriptionId, UUID recordId, UUID patientId, UUID doctorId,
                                       UUID departmentId, LocalDate prescribedDate, BigDecimal totalAmount,
                                       List<PrescriptionLine> lines, Instant createdAt) {
        return new Prescription(prescriptionId, recordId, patientId, doctorId, departmentId,
                prescribedDate, totalAmount, List.copyOf(lines), createdAt);
    }

   public BigDecimal computeTotal() {
        return computeTotalFrom(lines);
    }

    private static BigDecimal computeTotalFrom(List<PrescriptionLine> lines){
               return lines.stream()
                          .map(PrescriptionLine::getLineTotal)
                          .reduce(BigDecimal.ZERO, BigDecimal::add)
                          .setScale(2, RoundingMode.HALF_UP);
    }

    /**
 * Bảo đảm một thuốc không xuất hiện nhiều lần trong cùng aggregate.
 */
private static void validateUniqueDrugs(List<PrescriptionLine> lines) {
    Set<UUID> seenDrugIds = new HashSet<>();

    for (PrescriptionLine line : lines) {
        if (!seenDrugIds.add(line.getDrugId())) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_DUPLICATE_DRUG",
                    "Một thuốc chỉ được xuất hiện một lần trong đơn");
        }
    }
}
}
