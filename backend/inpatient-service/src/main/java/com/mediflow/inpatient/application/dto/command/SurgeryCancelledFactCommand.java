package com.mediflow.inpatient.application.dto.command;

import java.time.Instant;
import java.util.UUID;

public record SurgeryCancelledFactCommand(
        UUID maSuKien,
        int phienBan,
        String maTuongQuan,
        UUID maYLenhBenNgoai,
        UUID maDotNoiTru,
        String giaiDoanHuy,
        String lyDo,
        Instant huyLuc,
        Instant xayRaLuc) implements ExternalOrderFactCommand {
}
