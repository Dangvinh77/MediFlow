package com.mediflow.inpatient.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReleaseBedRequest(
        @NotNull UUID nguoiTraGiuong,
        @NotBlank @Size(max = 1000) String lyDo) {
}
