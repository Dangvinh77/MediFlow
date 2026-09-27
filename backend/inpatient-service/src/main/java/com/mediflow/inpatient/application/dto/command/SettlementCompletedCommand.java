package com.mediflow.inpatient.application.dto.command;

import com.mediflow.inpatient.domain.model.enums.SettlementOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SettlementCompletedCommand(
        UUID maSuKien,
        int phienBan,
        Instant xayRaLuc,
        String maTuongQuan,
        UUID maQuyetToan,
        UUID maDotNoiTru,
        UUID maTaiKhoan,
        BigDecimal tongTien,
        BigDecimal baoHiemThanhToan,
        BigDecimal benhNhanPhaiTra,
        BigDecimal daThanhToan,
        BigDecimal daHoanTien,
        BigDecimal soDu,
        SettlementOutcome ketQua,
        Instant hoanTatLuc) {
}
