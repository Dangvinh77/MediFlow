package com.mediflow.clinical.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FollowUpRecommendationDTO(
        UUID recommendationId,
        UUID sourceRecordId,
        UUID sourceRegistrationId,
        UUID patientId,
        UUID departmentId,
        UUID preferredDoctorId,
        LocalDate recommendedFrom,
        LocalDate recommendedThrough,
        String reason,
        String preparationInstructions,
        Instant createdAt) {}
