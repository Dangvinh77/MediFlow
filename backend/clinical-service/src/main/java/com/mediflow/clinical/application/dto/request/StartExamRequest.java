package com.mediflow.clinical.application.dto.request;

import jakarta.validation.Valid;

public record StartExamRequest(@Valid EmergencyOverrideRequest emergencyOverride) {
}
