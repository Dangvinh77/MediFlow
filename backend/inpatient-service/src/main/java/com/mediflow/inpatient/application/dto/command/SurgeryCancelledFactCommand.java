package com.mediflow.inpatient.application.dto.command;

import java.time.Instant;
import java.util.UUID;

public record SurgeryCancelledFactCommand(
        UUID maSuKien,
        int phienBan,
        String maTuongQuan,
        UUID maYLenhBenNgoai,
        UUID maDotNoiTru,
        UUID maLanHuy,
        String giaiDoanHuy,
        String lyDo,
        Instant huyLuc,
        Instant xayRaLuc,
        UUID maYeuCauMo,
        UUID maBenhNhan,
        UUID maKhoa,
        int phienBanCa,
        int phienBanNguon,
        String dauVanTai) implements ExternalOrderFactCommand {
}
