package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.model.enums.DischargeOutcome;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DischargeSummary(
        UUID maTomTat,
        UUID maDotNoiTru,
        String tomTatChanDoan,
        String tomTatDieuTri,
        DischargeOutcome ketQua,
        String keHoachTheoDoi,
        UUID nguoiDuyet,
        Instant thoiGianDuyet) {

    public DischargeSummary {
        Objects.requireNonNull(maTomTat);
        Objects.requireNonNull(maDotNoiTru);
        tomTatChanDoan = requireText(tomTatChanDoan);
        tomTatDieuTri = requireText(tomTatDieuTri);
        Objects.requireNonNull(ketQua);
        keHoachTheoDoi = requireText(keHoachTheoDoi);
        Objects.requireNonNull(nguoiDuyet);
        Objects.requireNonNull(thoiGianDuyet);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Discharge summary fields are required");
        }
        return value;
    }
}
