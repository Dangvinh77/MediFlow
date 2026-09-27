package com.mediflow.inpatient.application.dto.event;

import java.time.Instant;
import java.util.UUID;

public record AdmissionClosedEvent(
        UUID maDotNoiTru,
        UUID maBenhNhan,
        UUID maQuyetToan,
        UUID maPheDuyetDong,
        Instant thoiGianDong) {
}
