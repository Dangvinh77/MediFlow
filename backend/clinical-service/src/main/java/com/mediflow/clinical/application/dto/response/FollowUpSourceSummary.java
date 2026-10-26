package com.mediflow.clinical.application.dto.response;
import java.time.Instant;
import java.util.UUID;
public record FollowUpSourceSummary(UUID registrationId, UUID recordId, UUID patientId,
        UUID departmentId, UUID doctorId, Instant completedAt, FollowUpRecommendationDTO recommendation) {}
