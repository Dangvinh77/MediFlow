package com.mediflow.inpatient.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DepositTopupRequest(
        UUID maYeuCauBoSung,
        UUID maSuKien,
        UUID maDotNoiTru,
        UUID maTaiKhoan,
        BigDecimal soDuHienTai,
        BigDecimal soTienYeuCau,
        String lyDo,
        Instant thoiGianYeuCau) {

    public DepositTopupRequest {
        Objects.requireNonNull(maYeuCauBoSung);
        Objects.requireNonNull(maSuKien);
        Objects.requireNonNull(maDotNoiTru);
        Objects.requireNonNull(maTaiKhoan);
        Objects.requireNonNull(soDuHienTai);
        Objects.requireNonNull(soTienYeuCau);
        if (soTienYeuCau.signum() <= 0 || lyDo == null || lyDo.isBlank()) {
            throw new IllegalArgumentException("Top-up request is invalid");
        }
        Objects.requireNonNull(thoiGianYeuCau);
    }
}
