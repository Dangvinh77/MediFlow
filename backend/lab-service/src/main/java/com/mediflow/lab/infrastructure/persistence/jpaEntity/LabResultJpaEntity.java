package com.mediflow.lab.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lab_result")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LabResultJpaEntity {
    @Id @Column(name = "result_id", nullable = false) private UUID resultId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false) private LabTestJpaEntity test;
    @Column(name = "indicator", nullable = false, length = 100) private String indicator;
    @Column(name = "value", nullable = false, length = 50) private String value;
    @Column(name = "unit", length = 20) private String unit;
    @Column(name = "reference_range", length = 50) private String referenceRange;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
