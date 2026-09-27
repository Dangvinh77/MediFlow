package com.mediflow.lab.infrastructure.persistence.jpaEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabTestJpaEntity;

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

/** JPA row for a single clearance target projected onto a local test. */
@Entity
@Table(name = "lab_financial_clearance")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LabFinancialClearanceJpaEntity {
    @Id @Column(name = "clearance_target_id", nullable = false) private UUID clearanceTargetId;
    @Column(name = "clearance_id", nullable = false) private UUID clearanceId;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false) private LabTestJpaEntity test;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Enumerated(EnumType.STRING) @Column(name = "care_episode_type", nullable = false, length = 32)
    private CareEpisodeType careEpisodeType;
    @Column(name = "care_episode_id", nullable = false) private UUID careEpisodeId;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)") private String currency;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "emergency_override", nullable = false) private boolean emergencyOverride;
    @Column(name = "granted_at", nullable = false) private Instant grantedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
