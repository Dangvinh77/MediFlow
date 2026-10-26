package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Immutable clinical advice for a future visit; it never reserves a consultation seat. */
public record FollowUpRecommendation(
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
        Instant createdAt,
        UUID createdByAccountId,
        UUID createdByStaffId) {

    public FollowUpRecommendation {
        Objects.requireNonNull(recommendationId, "recommendation id is required");
        Objects.requireNonNull(sourceRecordId, "source record id is required");
        Objects.requireNonNull(sourceRegistrationId, "source registration id is required");
        Objects.requireNonNull(patientId, "patient id is required");
        Objects.requireNonNull(departmentId, "department id is required");
        Objects.requireNonNull(preferredDoctorId, "preferred doctor id is required");
        Objects.requireNonNull(recommendedFrom, "recommended start date is required");
        Objects.requireNonNull(recommendedThrough, "recommended end date is required");
        Objects.requireNonNull(createdAt, "creation time is required");
        Objects.requireNonNull(createdByAccountId, "creator account id is required");
        Objects.requireNonNull(createdByStaffId, "creator staff id is required");
        if (recommendedThrough.isBefore(recommendedFrom)) {
            throw new IllegalArgumentException("FOLLOW_UP_WINDOW_INVALID");
        }
        if (reason == null || reason.isBlank() || reason.length() > 1000
                || preparationInstructions != null && preparationInstructions.length() > 2000) {
            throw new IllegalArgumentException("FOLLOW_UP_RECOMMENDATION_INVALID");
        }
    }
}
