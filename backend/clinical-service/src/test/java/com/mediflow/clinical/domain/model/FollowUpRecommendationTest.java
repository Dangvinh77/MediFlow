package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FollowUpRecommendationTest {
    private static final UUID RECOMMENDATION_ID = UUID.randomUUID();
    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID REGISTRATION_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-10-26T02:00:00Z");

    @Test
    void allowsAnExactDateAndAnOverdueRecommendationWindow() {
        var recommendation = new FollowUpRecommendation(RECOMMENDATION_ID, RECORD_ID, REGISTRATION_ID,
                PATIENT_ID, DEPARTMENT_ID, DOCTOR_ID, LocalDate.parse("2026-10-25"),
                LocalDate.parse("2026-10-25"), "Review symptoms", null, CREATED_AT, ACCOUNT_ID, DOCTOR_ID);

        assertThat(recommendation.recommendedFrom()).isEqualTo(LocalDate.parse("2026-10-25"));
        assertThat(recommendation.recommendedThrough()).isEqualTo(LocalDate.parse("2026-10-25"));
    }

    @Test
    void rejectsAReversedRecommendationWindow() {
        assertThatThrownBy(() -> new FollowUpRecommendation(RECOMMENDATION_ID, RECORD_ID, REGISTRATION_ID,
                PATIENT_ID, DEPARTMENT_ID, DOCTOR_ID, LocalDate.parse("2026-10-27"),
                LocalDate.parse("2026-10-26"), "Review symptoms", null, CREATED_AT, ACCOUNT_ID, DOCTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FOLLOW_UP_WINDOW_INVALID");
    }

    @Test
    void requiresAReasonAndBoundsPreparationInstructions() {
        assertThatThrownBy(() -> new FollowUpRecommendation(RECOMMENDATION_ID, RECORD_ID, REGISTRATION_ID,
                PATIENT_ID, DEPARTMENT_ID, DOCTOR_ID, LocalDate.parse("2026-10-26"),
                LocalDate.parse("2026-10-27"), "   ", null, CREATED_AT, ACCOUNT_ID, DOCTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FOLLOW_UP_RECOMMENDATION_INVALID");

        assertThatThrownBy(() -> new FollowUpRecommendation(RECOMMENDATION_ID, RECORD_ID, REGISTRATION_ID,
                PATIENT_ID, DEPARTMENT_ID, DOCTOR_ID, LocalDate.parse("2026-10-26"),
                LocalDate.parse("2026-10-27"), "Review symptoms", "x".repeat(2001), CREATED_AT,
                ACCOUNT_ID, DOCTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FOLLOW_UP_RECOMMENDATION_INVALID");
    }
}
