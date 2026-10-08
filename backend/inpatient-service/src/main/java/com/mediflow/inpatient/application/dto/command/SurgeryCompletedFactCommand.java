package com.mediflow.inpatient.application.dto.command;

import java.time.Instant;
import java.util.UUID;

public record SurgeryCompletedFactCommand(
        UUID maSuKien,
        int phienBan,
        String maTuongQuan,
        UUID maYLenhBenNgoai,
        UUID maDotNoiTru,
        UUID maKetQuaMo,
        String tomTatBienChung,
        Instant hoanTatLuc,
        Instant xayRaLuc,
        UUID maYeuCauMo,
        UUID maBenhNhan,
        UUID maKhoa,
        int phienBanCa,
        int phienBanNguon,
        String dauVanTai) implements ExternalOrderFactCommand {
}
