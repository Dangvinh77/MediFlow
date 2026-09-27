package com.mediflow.inpatient.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CloseAdmissionRequest(@NotNull UUID nguoiDong, @Valid CloseOverrideRequest pheDuyet) {
}
