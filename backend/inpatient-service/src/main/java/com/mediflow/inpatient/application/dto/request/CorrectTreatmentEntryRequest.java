package com.mediflow.inpatient.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CorrectTreatmentEntryRequest(
        @NotBlank @Size(max = 10000) String noiDungDaDinhChinh,
        @NotNull UUID nguoiGhi,
        @NotNull @PastOrPresent Instant thoiGianGhi) {
}
