package com.mediflow.lab.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.lab.domain.model.CareEpisodeType;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** JPA row for a clinician-approved emergency operation. */
@Entity
@Table(name = "lab_emergency_override")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LabEmergencyOverrideJpaEntity {
    @Id @Column(name = "override_id", nullable = false) private UUID overrideId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false) private LabTestJpaEntity test;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Enumerated(EnumType.STRING) @Column(name = "care_episode_type", nullable = false, length = 32)
    private CareEpisodeType careEpisodeType;
    @Column(name = "care_episode_id", nullable = false) private UUID careEpisodeId;
    @Column(name = "approved_by", nullable = false) private UUID approvedBy;
    @Column(name = "approver_role", nullable = false, length = 32) private String approverRole;
    @Column(name = "reason", nullable = false, columnDefinition = "text") private String reason;
    @Column(name = "approved_at", nullable = false) private Instant approvedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
