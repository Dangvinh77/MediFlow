package com.mediflow.surgery.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Append-only state transition facts owned by the case aggregate. */
public record SurgeryStateChange(
        SurgeryStatus trangThaiCu,
        SurgeryStatus trangThaiMoi,
        UUID nguoiThucHien,
        String lyDo,
        Instant thoiDiem) {
}
