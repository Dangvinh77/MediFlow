package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.time.Instant;
import java.util.UUID;

/** Immutable evidence of the guards checked at one readiness decision. */
public record ReadinessSnapshot(
        UUID maAnhChupSanSang,
        UUID maCaPhauThuat,
        boolean chiDinhHopLe,
        boolean checklistBatBuocDayDu,
        boolean dongYPhauThuatActive,
        boolean dongYGayMeActive,
        boolean kipPhauThuatDuDieuKien,
        boolean lichPhongDaXacNhan,
        boolean xacNhanTaiChinhHopLe,
        Instant thoiGianDanhGia) {

    public ReadinessSnapshot {
        if (maAnhChupSanSang == null || maCaPhauThuat == null || thoiGianDanhGia == null) {
            throw new SurgeryRuleException(
                    "SURGERY_READINESS_SNAPSHOT_INVALID",
                    "Snapshot readiness phải có mã, ca phẫu thuật và thời điểm đánh giá");
        }
    }

    /** V1 requires both separately recorded consents and never uses an emergency override. */
    public boolean isReady() {
        return chiDinhHopLe
                && checklistBatBuocDayDu
                && dongYPhauThuatActive
                && dongYGayMeActive
                && kipPhauThuatDuDieuKien
                && lichPhongDaXacNhan
                && xacNhanTaiChinhHopLe;
    }

    public UUID snapshotId() {
        return maAnhChupSanSang;
    }

    public UUID surgeryCaseId() {
        return maCaPhauThuat;
    }

    public Instant evaluatedAt() {
        return thoiGianDanhGia;
    }
}
