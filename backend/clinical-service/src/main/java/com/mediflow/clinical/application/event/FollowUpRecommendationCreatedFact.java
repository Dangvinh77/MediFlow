package com.mediflow.clinical.application.event;

import java.time.LocalDate;
import java.util.UUID;

/** Deliberately excludes clinical prose; this fact contains only linkage identifiers and dates. */
public record FollowUpRecommendationCreatedFact(
        UUID recommendationId,
        UUID sourceRecordId,
        UUID sourceRegistrationId,
        UUID patientId,
        UUID departmentId,
        UUID preferredDoctorId,
        LocalDate recommendedFrom,
        LocalDate recommendedThrough) {}
