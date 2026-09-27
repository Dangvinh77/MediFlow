package com.mediflow.inpatient.application.dto.event;

import java.math.BigDecimal;
import java.util.UUID;

public record AdmissionDepositRequestedEvent(
        UUID maDotNoiTru,
        UUID maBenhNhan,
        UUID maKhoa,
        String loaiTapNoiTru,
        UUID maTapNoiTru,
        String loaiNguon,
        UUID maNguon,
        String maBangGia,
        BigDecimal soTienGoiY,
        String lyDo) {
}
