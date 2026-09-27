package com.mediflow.inpatient.application.dto.command;

import com.mediflow.inpatient.domain.model.enums.CareEpisodeType;
import com.mediflow.inpatient.domain.model.enums.ClearancePurpose;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinancialClearanceCommand(
        UUID maSuKien,
        int phienBan,
        Instant xayRaLuc,
        String maTuongQuan,
        UUID maXacNhan,
        UUID maHoaDon,
        UUID maTaiKhoan,
        UUID maBenhNhan,
        CareEpisodeType loaiTapNoiTru,
        UUID maTapNoiTru,
        ClearancePurpose mucDich,
        UUID maDotNoiTru,
        BigDecimal soTien,
        String tienTe,
        String phuongThucThanhToan,
        Instant hetHanLuc,
        boolean capCuuNgoaiLe) {
}
