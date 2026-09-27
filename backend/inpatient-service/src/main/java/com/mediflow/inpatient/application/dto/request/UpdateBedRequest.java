package com.mediflow.inpatient.application.dto.request;

import com.mediflow.inpatient.domain.model.enums.BedStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateBedRequest(
        @NotBlank @Size(max = 32) String loaiGiuong,
        @NotNull BedStatus status,
        boolean active) {
}
