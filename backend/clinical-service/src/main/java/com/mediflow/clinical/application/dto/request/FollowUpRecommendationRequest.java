package com.mediflow.clinical.application.dto.request;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The exact four-field request accepted when a doctor records follow-up advice. */
public record FollowUpRecommendationRequest(
        @NotNull LocalDate recommendedFrom,
        @NotNull LocalDate recommendedThrough,
        @NotBlank @Size(max = 1000) String reason,
        @Size(max = 2000) String preparationInstructions) {

    @JsonAnySetter
    public void rejectUnknownProperty(String property, Object ignored) {
        throw new IllegalArgumentException("Unsupported follow-up recommendation property: " + property);
    }
}
