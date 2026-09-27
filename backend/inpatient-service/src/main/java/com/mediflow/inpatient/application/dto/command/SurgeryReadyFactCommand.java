package com.mediflow.inpatient.application.dto.command;

import java.time.Instant;
import java.util.UUID;

public record SurgeryReadyFactCommand(
        UUID maSuKien,
        int phienBan,
        String maTuongQuan,
        UUID maYLenhBenNgoai,
        UUID maDotNoiTru,
        UUID maLichMo,
        UUID maAnhChupSanSang,
        Instant sanSangLuc,
        Instant xayRaLuc) implements ExternalOrderFactCommand {
}
