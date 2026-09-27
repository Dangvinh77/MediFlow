package com.mediflow.inpatient.application.dto.command;

import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;

import java.time.Instant;
import java.util.UUID;

public record AdmissionRequestedCommand(
        UUID maSuKien,
        int phienBan,
        Instant xayRaLuc,
        String maTuongQuan,
        UUID maYeuCauNoiTru,
        UUID maHoSo,
        UUID maBenhNhan,
        UUID maKhoa,
        UUID nguoiYeuCau,
        String tomTatChanDoan,
        AdmissionPriority doUuTien,
        boolean capCuu,
        Instant thoiGianYeuCau) {
}
