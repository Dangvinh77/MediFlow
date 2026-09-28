package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryActorType;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Persistence-only row; domain rules remain in SurgeryCase. */
@Entity
@Table(name = "surgery_case")
public class SurgeryCaseJpaEntity {

    @Id @Column(name = "surgery_case_id", nullable = false) UUID surgeryCaseId;
    @Column(name = "surgery_request_id", nullable = false) UUID surgeryRequestId;
    @Enumerated(EnumType.STRING) @Column(name = "episode_type", nullable = false, length = 24)
    CareEpisodeType episodeType;
    @Column(name = "episode_id", nullable = false) UUID episodeId;
    @Column(name = "admission_id") UUID admissionId;
    @Column(name = "medical_record_id") UUID medicalRecordId;
    @Column(name = "patient_id", nullable = false) UUID patientId;
    @Column(name = "department_id", nullable = false) UUID departmentId;
    @Column(name = "requested_by", nullable = false) UUID requestedBy;
    @Column(name = "procedure_code", nullable = false, length = 64) String procedureCode;
    @Column(name = "indication", nullable = false, columnDefinition = "text") String indication;
    @Enumerated(EnumType.STRING) @Column(name = "priority", nullable = false, length = 16)
    SurgeryPriority priority;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 24)
    SurgeryStatus status;
    @Column(name = "readiness_snapshot_id") UUID readinessSnapshotId;
    @Column(name = "requested_at", nullable = false) Instant requestedAt;
    @Column(name = "ready_at") Instant readyAt;
    @Column(name = "started_at") Instant startedAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "cancelled_at") Instant cancelledAt;
    @Enumerated(EnumType.STRING) @Column(name = "cancellation_actor_type", length = 8)
    SurgeryActorType cancellationActorType;
    @Column(name = "cancellation_account_id") UUID cancellationAccountId;
    @Column(name = "cancellation_staff_id") UUID cancellationStaffId;
    @Column(name = "cancellation_system_producer", length = 128) String cancellationSystemProducer;
    @Column(name = "cancellation_reason", length = 1000) String cancellationReason;
    @Column(name = "revision", nullable = false) long revision;
    @Version @Column(name = "technical_version", nullable = false) Long technicalVersion;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    protected SurgeryCaseJpaEntity() {
    }
}
