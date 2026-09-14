package com.mediflow.pharmacy.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable cursor/lease row for a pharmacy background job. */
@Entity
@Table(name = "PHARMACY_SCHEDULER_LEASE")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacySchedulerLeaseJpaEntity {

    @Id
    @Column(name = "job_name", length = 100, nullable = false)
    private String jobName;

    @Column(name = "cursor_id")
    private UUID cursorId;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_token", nullable = false)
    private UUID leaseToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
