package com.mediflow.inpatient.application.dto.command;

import java.time.Instant;
import java.util.UUID;

public record SurgeryCaseCreatedCommand(
        UUID maSuKien,
        int phienBan,
        String maTuongQuan,
        UUID maCaMo,
        UUID maYeuCauMo,
        UUID maDotNoiTru,
        UUID maBenhNhan,
        UUID maKhoa,
        int phienBanCa,
        int phienBanNguon,
        String maThuThuat,
        Instant yeuCauLuc,
        String dauVanTai,
        Instant xayRaLuc) {
}
