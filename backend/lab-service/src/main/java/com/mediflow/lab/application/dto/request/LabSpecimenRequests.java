package com.mediflow.lab.application.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class LabSpecimenRequests {
    private LabSpecimenRequests() {
    }

    /** {@code additionalTestIds}: other tests of the same patient served by this specimen. */
    public record CollectSpecimenRequest(@NotBlank @Size(max = 40) String specimenType,
            @Size(max = 20) List<UUID> additionalTestIds, UUID replacesSpecimenId) {
    }

    public record SpecimenDecisionRequest(@NotBlank @Pattern(regexp = "ACCEPTED|REJECTED") String decision,
            @Size(max = 500) String reason) {
    }
}
