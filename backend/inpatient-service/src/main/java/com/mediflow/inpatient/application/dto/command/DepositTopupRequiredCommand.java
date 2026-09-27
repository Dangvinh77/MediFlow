package com.mediflow.inpatient.application.dto.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DepositTopupRequiredCommand(
        UUID maSuKien,
        int phienBan,
        Instant xayRaLuc,
        String maTuongQuan,
        UUID maTaiKhoan,
        UUID maDotNoiTru,
        BigDecimal soDuHienTai,
        BigDecimal soTienYeuCau,
        String lyDo) {
}
