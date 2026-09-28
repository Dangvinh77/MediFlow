package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.util.UUID;

/** The exact clinical episode selected for this case; all other IDs remain context only. */
public record CareEpisode(
        CareEpisodeType loaiTapDieuTri,
        UUID maTapDieuTri,
        UUID maNhapVien,
        UUID maHoSo) {

    public CareEpisode {
        if (loaiTapDieuTri == null || maTapDieuTri == null) {
            throw new SurgeryRuleException(
                    "SURGERY_EPISODE_REQUIRED", "Loại và mã đợt điều trị là bắt buộc");
        }
        if (loaiTapDieuTri == CareEpisodeType.ADMISSION
                && (maNhapVien == null || !maTapDieuTri.equals(maNhapVien))) {
            throw new SurgeryRuleException(
                    "SURGERY_EPISODE_MISMATCH",
                    "Đợt điều trị nội trú phải trùng chính xác mã lần nhập viện");
        }
        if (loaiTapDieuTri == CareEpisodeType.OUTPATIENT_VISIT && maNhapVien != null) {
            throw new SurgeryRuleException(
                    "SURGERY_EPISODE_MISMATCH",
                    "Đợt ngoại trú không được chọn mã lần nhập viện làm episode");
        }
    }

    public CareEpisodeType type() {
        return loaiTapDieuTri;
    }

    public UUID episodeId() {
        return maTapDieuTri;
    }

    public UUID admissionId() {
        return maNhapVien;
    }

    public UUID recordId() {
        return maHoSo;
    }
}
