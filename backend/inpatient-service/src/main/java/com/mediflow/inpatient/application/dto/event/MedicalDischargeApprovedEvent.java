package com.mediflow.inpatient.application.dto.event;

import java.time.Instant;
import java.util.UUID;

public record MedicalDischargeApprovedEvent(
        UUID maDotNoiTru,
        UUID maBenhNhan,
        UUID maTomTat,
        UUID nguoiDuyet,
        Instant thoiGianDuyet) {
}
