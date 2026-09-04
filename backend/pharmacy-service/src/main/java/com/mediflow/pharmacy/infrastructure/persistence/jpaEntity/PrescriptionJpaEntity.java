package com.mediflow.pharmacy.infrastructure.persistence.jpaEntity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity của aggregate {@code PRESCRIPTION} và các dòng thuốc sở hữu.
 *
 * <p>Các mã hồ sơ, bệnh nhân, bác sĩ và khoa là UUID trần; pharmacy-service không tạo quan hệ
 * JPA sang bounded context khác. Trạng thái và trường hủy phản ánh vòng đời của aggregate.</p>
 */
@Entity
@Table(name = "PRESCRIPTION")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PrescriptionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "prescription_id", updatable = false, nullable = false)
    private UUID prescriptionId;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "prescribed_date", nullable = false)
    private LocalDate prescribedDate;

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private PrescriptionStatus status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineId ASC")
    @Builder.Default
    private List<PrescriptionLineJpaEntity> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Thêm một dòng và đồng thời thiết lập phía sở hữu của quan hệ hai chiều.
     *
     * @param line dòng đơn thuốc cần gắn vào aggregate
     */
    public void addLine(PrescriptionLineJpaEntity line) {
        lines.add(line);
        line.setPrescription(this);
    }
}
