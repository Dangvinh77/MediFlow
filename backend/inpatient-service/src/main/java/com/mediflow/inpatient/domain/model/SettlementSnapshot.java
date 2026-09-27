package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.model.enums.SettlementOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable billing settlement projection; Inpatient never recalculates these amounts. */
public record SettlementSnapshot(
        UUID maQuyetToan,
        UUID maSuKien,
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

    public SettlementSnapshot {
        Objects.requireNonNull(maQuyetToan);
        Objects.requireNonNull(maSuKien);
        Objects.requireNonNull(maDotNoiTru);
        Objects.requireNonNull(maTaiKhoan);
        Objects.requireNonNull(tongTien);
        Objects.requireNonNull(baoHiemThanhToan);
        Objects.requireNonNull(benhNhanPhaiTra);
        Objects.requireNonNull(daThanhToan);
        Objects.requireNonNull(daHoanTien);
        Objects.requireNonNull(soDu);
        Objects.requireNonNull(ketQua);
        Objects.requireNonNull(hoanTatLuc);
    }
}
