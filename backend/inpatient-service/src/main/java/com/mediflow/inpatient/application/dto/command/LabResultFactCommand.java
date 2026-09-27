package com.mediflow.inpatient.application.dto.command;

import java.time.Instant;
import java.util.UUID;

public record LabResultFactCommand(
        UUID maSuKien,
        UUID maYLenhBenNgoai,
        UUID maDotNoiTru,
        UUID maBenhNhan,
        int phienBanKetQua,
        String ketLuan,
        Instant xayRaLuc) implements ExternalOrderFactCommand {
}
