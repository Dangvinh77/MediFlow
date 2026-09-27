package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AdmissionStatusHistory(
        UUID maLichSu,
        UUID maDotNoiTru,
        AdmissionStatus trangThaiTruoc,
        AdmissionStatus trangThaiSau,
        UUID nguoiThucHien,
        String lyDo,
        String maTuongQuan,
        Instant thoiGianThayDoi) {

    public AdmissionStatusHistory {
        Objects.requireNonNull(maLichSu);
        Objects.requireNonNull(maDotNoiTru);
        Objects.requireNonNull(trangThaiSau);
        if (maTuongQuan == null || maTuongQuan.isBlank()) {
            throw new IllegalArgumentException("Correlation ID is required for admission history");
        }
        Objects.requireNonNull(thoiGianThayDoi);
    }
}
