package com.mediflow.clinical.infrastructure.persistence.jpaEntity;

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

@Entity
@Table(name = "clinical_emergency_override")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalEmergencyOverrideJpaEntity {
    @Id
    @Column(name = "override_id", nullable = false)
    private UUID overrideId;
    @Column(name = "appointment_id")
    private UUID appointmentId;
    @Column(name = "record_id")
    private UUID recordId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Column(name = "care_episode_id", nullable = false)
    private UUID careEpisodeId;
    @Column(name = "approved_by", nullable = false)
    private UUID approvedBy;
    @Column(name = "approver_role", nullable = false, length = 32)
    private String approverRole;
    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;
    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
