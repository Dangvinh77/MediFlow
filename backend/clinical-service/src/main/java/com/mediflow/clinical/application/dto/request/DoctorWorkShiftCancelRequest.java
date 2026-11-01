package com.mediflow.clinical.application.dto.request;
import jakarta.validation.constraints.*;
public record DoctorWorkShiftCancelRequest(@Positive int expectedRevision,
        @NotBlank @Size(max=500) String reason) {}
