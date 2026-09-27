package com.mediflow.inpatient.application.dto.event;

import java.time.Instant;
import java.util.UUID;

public record AdmissionStartedEvent(
        UUID maDotNoiTru,
        UUID maBenhNhan,
        UUID maGiuong,
        UUID maKhoa,
        Instant thoiGianNhapVien,
        boolean capCuu,
        UUID maPheDuyetNgoaiLe) {
}
