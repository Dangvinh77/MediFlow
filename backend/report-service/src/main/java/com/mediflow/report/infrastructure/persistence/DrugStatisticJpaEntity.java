package com.mediflow.report.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persistence-only mapping for {@code drug_statistic}. */
@Entity
@Table(name = "DRUG_STATISTIC")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugStatisticJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "statistic_id", updatable = false, nullable = false)
    private UUID statisticId;

    @Column(name = "drug_id", nullable = false)
    private UUID drugId;

    @Column(name = "drug_name", length = 150, nullable = false)
    private String drugName;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "dispensed_quantity", nullable = false)
    private int dispensedQuantity;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
