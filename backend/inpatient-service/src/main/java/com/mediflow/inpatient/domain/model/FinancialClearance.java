package com.mediflow.inpatient.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable projection of Billing's admission deposit clearance. */
public record FinancialClearance(
        UUID maXacNhan,
        UUID maSuKien,
        UUID maHoaDon,
        UUID maTaiKhoan,
        UUID maDotNoiTru,
        UUID maBenhNhan,
        BigDecimal soTien,
        String tienTe,
        String phuongThucThanhToan,
        Instant hetHanLuc,
        boolean capCuuNgoaiLe,
        Instant thoiGianCap) {

    public FinancialClearance {
        Objects.requireNonNull(maXacNhan);
        Objects.requireNonNull(maSuKien);
        Objects.requireNonNull(maHoaDon);
        Objects.requireNonNull(maTaiKhoan);
        Objects.requireNonNull(maDotNoiTru);
        Objects.requireNonNull(maBenhNhan);
        Objects.requireNonNull(soTien);
        if (soTien.signum() < 0 || tienTe == null || tienTe.length() != 3
                || phuongThucThanhToan == null || phuongThucThanhToan.isBlank()) {
            throw new IllegalArgumentException("Financial clearance projection is invalid");
        }
        Objects.requireNonNull(thoiGianCap);
    }
}
