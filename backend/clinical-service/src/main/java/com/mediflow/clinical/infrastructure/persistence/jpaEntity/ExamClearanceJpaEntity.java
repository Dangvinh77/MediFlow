package com.mediflow.clinical.infrastructure.persistence.jpaEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.model.CareEpisodeType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "exam_clearance")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamClearanceJpaEntity {
    @Id
    @Column(name = "clearance_id", nullable = false)
    private UUID clearanceId;
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    @Column(name = "appointment_id")
    private UUID appointmentId;
    @Column(name = "record_id")
    private UUID recordId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Enumerated(EnumType.STRING)
    @Column(name = "care_episode_type", nullable = false, length = 32)
    private CareEpisodeType careEpisodeType;
    @Column(name = "care_episode_id", nullable = false)
    private UUID careEpisodeId;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "emergency_override", nullable = false)
    private boolean emergencyOverride;
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
