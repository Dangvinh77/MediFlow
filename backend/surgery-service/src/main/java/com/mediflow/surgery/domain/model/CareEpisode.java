package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.util.UUID;

/** The exact clinical episode selected for this case; all other IDs remain context only. */
public record CareEpisode(
        CareEpisodeType episodeType,
        UUID episodeId,
        UUID admissionId,
        UUID medicalRecordId) {

    public CareEpisode {
        if (episodeType == null || episodeId == null) {
            throw new SurgeryRuleException(
                    "SURGERY_EPISODE_REQUIRED", "Loại và mã đợt điều trị là bắt buộc");
        }
        if (episodeType == CareEpisodeType.ADMISSION
                && (admissionId == null || !episodeId.equals(admissionId))) {
            throw new SurgeryRuleException(
                    "SURGERY_EPISODE_MISMATCH",
                    "Đợt điều trị nội trú phải trùng chính xác mã lần nhập viện");
        }
        if (episodeType == CareEpisodeType.OUTPATIENT_VISIT && admissionId != null) {
            throw new SurgeryRuleException(
                    "SURGERY_EPISODE_MISMATCH",
                    "Đợt ngoại trú không được chọn mã lần nhập viện làm episode");
        }
    }

    public CareEpisodeType type() {
        return episodeType;
    }

    public UUID episodeId() {
        return episodeId;
    }

    public UUID admissionId() {
        return admissionId;
    }

    public UUID recordId() {
        return medicalRecordId;
    }
}
