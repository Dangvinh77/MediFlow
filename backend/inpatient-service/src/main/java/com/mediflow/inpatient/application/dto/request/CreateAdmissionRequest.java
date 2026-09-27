package com.mediflow.inpatient.application.dto.request;

import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateAdmissionRequest(
        @NotNull UUID maYeuCauNoiTru,
        @NotNull UUID maHoSoNguon,
        @NotNull UUID maBenhNhan,
        @NotNull UUID maKhoa,
        @NotNull UUID nguoiYeuCau,
        @NotBlank @Size(max = 4000) String tomTatChanDoan,
        @NotNull AdmissionPriority doUuTien,
        boolean capCuu,
        @NotNull Instant thoiGianYeuCau) {
}
