package com.mediflow.inpatient.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record EmergencyOverrideRequest(
        @NotNull UUID maPheDuyet,
        @NotNull UUID nguoiDuyet,
        @NotBlank @Size(max = 32) String vaiTroNguoiDuyet,
        @NotBlank @Size(max = 1000) String lyDo,
        @NotNull @PastOrPresent Instant thoiGianDuyet) {
}
