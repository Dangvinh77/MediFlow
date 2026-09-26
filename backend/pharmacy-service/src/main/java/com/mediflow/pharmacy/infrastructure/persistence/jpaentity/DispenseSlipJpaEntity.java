package com.mediflow.pharmacy.infrastructure.persistence.jpaentity;

import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.DispenseActorType;
import java.time.Instant;
import java.util.UUID;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** JPA entity lưu bằng chứng trạng thái của quy trình xuất thuốc cho một prescription. */
@Entity
@Table(name = "DISPENSE_SLIP")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Enumerated(EnumType.STRING)
    @Column(name = "dispensed_actor_type", length = 30)
    private DispenseActorType dispensedActorType;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
