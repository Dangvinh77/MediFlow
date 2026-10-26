package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.port.out.FollowUpRecommendationRepositoryPort;
import com.mediflow.clinical.domain.model.FollowUpRecommendation;
import com.mediflow.common.exception.DuplicateResourceException;

@Component
public class FollowUpRecommendationPersistenceAdapter implements FollowUpRecommendationRepositoryPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public FollowUpRecommendationPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public FollowUpRecommendation save(FollowUpRecommendation recommendation, UUID eventId, String correlationId) {
        try {
            jdbc.update("""
                    INSERT INTO follow_up_recommendation(recommendation_id, source_record_id, source_registration_id,
                        patient_id, department_id, preferred_doctor_id, recommended_from, recommended_through,
                        reason, preparation_instructions, created_by_account_id, created_by_staff_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, recommendation.recommendationId(), recommendation.sourceRecordId(),
                    recommendation.sourceRegistrationId(), recommendation.patientId(), recommendation.departmentId(),
                    recommendation.preferredDoctorId(), recommendation.recommendedFrom(),
                    recommendation.recommendedThrough(), recommendation.reason(),
                    recommendation.preparationInstructions(), recommendation.createdByAccountId(),
                    recommendation.createdByStaffId(), Timestamp.from(recommendation.createdAt()));
        } catch (DuplicateKeyException duplicate) {
            throw new DuplicateResourceException("FOLLOW_UP_RECOMMENDATION_EXISTS",
                    "A follow-up recommendation already exists for this completed record");
        }

        try {
            String afterState = objectMapper.writeValueAsString(recommendation);
            jdbc.update("""
                    INSERT INTO follow_up_recommendation_audit(audit_id, recommendation_id, action,
                        actor_account_id, actor_staff_id, correlation_id, event_id, after_state, occurred_at)
                    VALUES (?, ?, 'CREATED', ?, ?, ?, ?, ?::jsonb, ?)
                    """, UUID.randomUUID(), recommendation.recommendationId(),
                    recommendation.createdByAccountId(), recommendation.createdByStaffId(), correlationId,
                    eventId, afterState, Timestamp.from(recommendation.createdAt()));
        } catch (IOException serializationFailure) {
            throw new IllegalStateException("Follow-up recommendation audit could not be serialized", serializationFailure);
        }
        return recommendation;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FollowUpRecommendation> findBySourceRecordId(UUID sourceRecordId) {
        return jdbc.query("""
                SELECT * FROM follow_up_recommendation WHERE source_record_id = ?
                """, this::mapOne, sourceRecordId).stream().findFirst();
    }

    private FollowUpRecommendation mapOne(ResultSet row, int ignored) throws SQLException {
        return new FollowUpRecommendation(row.getObject("recommendation_id", UUID.class),
                row.getObject("source_record_id", UUID.class), row.getObject("source_registration_id", UUID.class),
                row.getObject("patient_id", UUID.class), row.getObject("department_id", UUID.class),
                row.getObject("preferred_doctor_id", UUID.class), row.getObject("recommended_from", java.time.LocalDate.class),
                row.getObject("recommended_through", java.time.LocalDate.class), row.getString("reason"),
                row.getString("preparation_instructions"), instant(row, "created_at"),
                row.getObject("created_by_account_id", UUID.class), row.getObject("created_by_staff_id", UUID.class));
    }

    private static java.time.Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp timestamp = row.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
