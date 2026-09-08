package com.mediflow.billing.infrastructure.persistence.jpaEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.mediflow.billing.domain.model.FeeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity của bảng {@code FEE} — một khoản viện phí. Không có quan hệ {@code @ManyToOne} sang
 * hóa đơn hay sang bounded context khác: chỉ giữ UUID trần theo docs/ai/08-persistence-naming.md §7.
 *
 * <p>Tên bảng/cột tiếng Anh theo thống nhất riêng cho nhánh C (06-billing.md §1). Các quy tắc
 * nghiệp vụ (amount &gt;= 0, departmentId != null...) sống trong {@code domain/model/Fee}; entity
 * này chỉ phản ánh lược đồ {@code V1__init.sql}.
 */
@Entity
@Table(name = "FEE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fee_id", updatable = false, nullable = false)
    private UUID feeId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "record_id")
    private UUID recordId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "source_ref_id")
    private UUID sourceRefId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", length = 10, nullable = false)
    private FeeType feeType;

    @Column(name = "incurred_date", nullable = false)
    private LocalDate incurredDate;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "is_paid", nullable = false)
    private boolean isPaid;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
