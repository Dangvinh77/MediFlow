package com.mediflow.pharmacy.infrastructure.persistence.jpaEntity;

import com.mediflow.pharmacy.domain.model.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** JPA entity lưu bằng chứng trạng thái của quy trình xuất thuốc cho một prescription. */
@Entity
@Table(name = "DISPENSE_SLIP")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class DispenseSlipJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "dispense_id", updatable = false, nullable = false)
    private UUID dispenseId;

    @Column(name = "prescription_id", nullable = false, unique = true)
    private UUID prescriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private DispenseStatus status;

    @Column(name = "dispensed_at")
    private Instant dispensedAt;

    @Column(name = "dispensed_by")
    private UUID dispensedBy;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
