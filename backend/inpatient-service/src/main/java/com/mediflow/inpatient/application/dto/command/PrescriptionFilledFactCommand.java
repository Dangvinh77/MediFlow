package com.mediflow.inpatient.application.dto.command;

import java.time.Instant;
import java.util.UUID;

public record PrescriptionFilledFactCommand(
        UUID maSuKien,
        int phienBan,
        String maTuongQuan,
        UUID maYLenhBenNgoai,
        UUID maDotNoiTru,
        UUID maBenhNhan,
        Instant daCapPhatLuc,
        Instant xayRaLuc) implements ExternalOrderFactCommand {
}
