package com.mediflow.inpatient.application.dto.request;

import com.mediflow.inpatient.domain.model.enums.DischargeOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record MedicalDischargeRequest(
        @NotNull UUID maTomTat,
        @NotBlank @Size(max = 4000) String tomTatChanDoan,
        @NotBlank @Size(max = 10000) String tomTatDieuTri,
        @NotNull DischargeOutcome ketQua,
        @NotBlank @Size(max = 4000) String keHoachTheoDoi,
        @NotNull UUID nguoiDuyet,
        @NotNull @PastOrPresent Instant thoiGianDuyet) {
}
