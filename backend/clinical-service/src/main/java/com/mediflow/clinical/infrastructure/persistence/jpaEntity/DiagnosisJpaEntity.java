package com.mediflow.clinical.infrastructure.persistence.jpaEntity;

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
@Table(name = "diagnosis")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DiagnosisJpaEntity {
    @Id @Column(name = "diagnosis_id", nullable = false) private UUID diagnosisId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false) private MedicalRecordJpaEntity record;
    @Column(name = "diagnosis_name", nullable = false, length = 255) private String diagnosisName;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "icd_code", length = 10) private String icdCode;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
