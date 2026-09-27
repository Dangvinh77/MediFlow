package com.mediflow.inpatient.domain.model;

import java.time.Instant;
import java.util.UUID;

public record EmergencyOverride(
        UUID maPheDuyet,
        UUID nguoiDuyet,
        String vaiTroNguoiDuyet,
        String lyDo,
        Instant thoiGianDuyet) {

    public EmergencyOverride {
        if (maPheDuyet == null || nguoiDuyet == null || vaiTroNguoiDuyet == null
                || vaiTroNguoiDuyet.isBlank() || lyDo == null || lyDo.isBlank()
                || thoiGianDuyet == null) {
            throw new IllegalArgumentException("Emergency admit override audit is incomplete");
        }
    }
}
