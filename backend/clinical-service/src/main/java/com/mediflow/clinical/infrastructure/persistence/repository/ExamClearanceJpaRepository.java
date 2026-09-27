package com.mediflow.clinical.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ExamClearanceJpaEntity;

public interface ExamClearanceJpaRepository extends JpaRepository<ExamClearanceJpaEntity, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO exam_clearance (
                clearance_id, event_id, invoice_id, account_id, appointment_id, record_id, patient_id,
                care_episode_type, care_episode_id, amount, currency, expires_at, emergency_override, granted_at
            ) VALUES (
                :clearanceId, :eventId, :invoiceId, :accountId, :appointmentId, :recordId, :patientId,
                :episodeType, :episodeId, :amount, :currency, :expiresAt, :emergencyOverride, :grantedAt
            )
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfEventNotClaimed(
            @Param("clearanceId") UUID clearanceId,
            @Param("eventId") UUID eventId,
            @Param("invoiceId") UUID invoiceId,
            @Param("accountId") UUID accountId,
            @Param("appointmentId") UUID appointmentId,
            @Param("recordId") UUID recordId,
            @Param("patientId") UUID patientId,
            @Param("episodeType") String episodeType,
            @Param("episodeId") UUID episodeId,
            @Param("amount") java.math.BigDecimal amount,
            @Param("currency") String currency,
            @Param("expiresAt") Instant expiresAt,
            @Param("emergencyOverride") boolean emergencyOverride,
            @Param("grantedAt") Instant grantedAt);

    Optional<ExamClearanceJpaEntity> findFirstByAppointmentIdOrderByGrantedAtDesc(UUID appointmentId);
    Optional<ExamClearanceJpaEntity> findFirstByRecordIdOrderByGrantedAtDesc(UUID recordId);

    @Query("""
            select c from ExamClearanceJpaEntity c
            where c.appointmentId = :appointmentId
              and (c.expiresAt is null or c.expiresAt > :at)
            order by c.grantedAt desc
            """)
    List<ExamClearanceJpaEntity> findValidForAppointment(
            @Param("appointmentId") UUID appointmentId, @Param("at") Instant at);

    @Query("""
            select c from ExamClearanceJpaEntity c
            where c.recordId = :recordId
              and (c.expiresAt is null or c.expiresAt > :at)
            order by c.grantedAt desc
            """)
    List<ExamClearanceJpaEntity> findValidForRecord(
            @Param("recordId") UUID recordId, @Param("at") Instant at);
}
