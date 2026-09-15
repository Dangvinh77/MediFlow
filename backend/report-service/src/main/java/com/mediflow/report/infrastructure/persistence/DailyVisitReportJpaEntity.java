package com.mediflow.report.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
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

/** Persistence-only mapping for {@code daily_visit_report}. */
@Entity
@Table(name = "DAILY_VISIT_REPORT")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyVisitReportJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id", updatable = false, nullable = false)
    private UUID reportId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "visit_count", nullable = false)
    private int visitCount;

    @Column(name = "lab_count", nullable = false)
    private int labCount;

    @Column(name = "prescription_count", nullable = false)
    private int prescriptionCount;

    @Column(name = "revenue", precision = 15, scale = 2, nullable = false)
    private BigDecimal revenue;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
