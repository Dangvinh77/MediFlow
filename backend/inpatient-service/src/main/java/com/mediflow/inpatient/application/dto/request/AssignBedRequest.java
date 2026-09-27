package com.mediflow.inpatient.application.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignBedRequest(@NotNull UUID maGiuong, @NotNull UUID nguoiPhanGiuong) {
}
