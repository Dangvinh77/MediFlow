package com.mediflow.clinical.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.model.AdmissionPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admission_referral")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionReferralJpaEntity {
    @Id
    @Column(name = "admission_request_id", nullable = false)
    private UUID admissionRequestId;
    @Column(name = "record_id", nullable = false, unique = true)
    private UUID recordId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Column(name = "department_id", nullable = false)
    private UUID departmentId;
    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;
    @Column(name = "diagnosis_summary", nullable = false, columnDefinition = "text")
    private String diagnosisSummary;
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private AdmissionPriority priority;
    @Column(name = "emergency", nullable = false)
    private boolean emergency;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
