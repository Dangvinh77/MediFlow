package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.model.enums.OverrideType;

import java.time.Instant;
import java.util.UUID;

public record CloseOverride(
        UUID maPheDuyet,
        OverrideType loaiPheDuyet,
        UUID nguoiDuyet,
        String vaiTroNguoiDuyet,
        String lyDo,
        Instant thoiGianDuyet) {

    public CloseOverride {
        if (maPheDuyet == null || loaiPheDuyet == null || nguoiDuyet == null
                || vaiTroNguoiDuyet == null || vaiTroNguoiDuyet.isBlank()
                || lyDo == null || lyDo.isBlank() || thoiGianDuyet == null) {
            throw new IllegalArgumentException("Admission close override audit is incomplete");
        }
    }
}
