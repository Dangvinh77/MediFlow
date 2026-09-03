package com.mediflow.pharmacy.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity cho bảng STOCK_RESERVATION — một dòng giữ chỗ tồn kho của một đơn.
 * Không có quan hệ @ManyToOne sang Drug/PRESCRIPTION (chỉ giữ UUID trần — theo chuẩn
 * bounded context, docs/ai/08-persistence-naming.md).
 */
@Entity
@Table(
        name = "STOCK_RESERVATION",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reservation_prescription_drug",
                        columnNames = {
                                "prescription_id",
                                "drug_id"
                        })
        })
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class StockReservationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reservation_id", updatable = false, nullable = false)
    private UUID reservationId;

    @Column(name = "drug_id", nullable = false)
    private UUID drugId;

    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ReservationStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
