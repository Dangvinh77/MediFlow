package com.mediflow.inpatient.application.dto.request;

import com.mediflow.inpatient.domain.model.enums.TreatmentEntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateTreatmentEntryRequest(
        @NotNull TreatmentEntryType loaiMuc,
        @NotBlank @Size(max = 10000) String noiDung,
        @NotNull UUID nguoiGhi,
        @NotNull @PastOrPresent Instant thoiGianGhi) {
}
