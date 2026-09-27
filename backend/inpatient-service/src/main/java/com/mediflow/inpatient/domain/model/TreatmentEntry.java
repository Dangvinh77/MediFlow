package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.domain.model.enums.TreatmentEntryType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An immutable timeline row. Corrections are separate rows and never rewrite the source entry. */
public record TreatmentEntry(
        UUID maMucDienBien,
        UUID maDotNoiTru,
        TreatmentEntryType loaiMuc,
        String noiDung,
        UUID nguoiGhi,
        Instant thoiGianGhi,
        UUID maMucBiDinhChinh) {

    public static TreatmentEntry create(UUID admissionId, TreatmentEntryType type, String content,
                                        UUID authoredBy, Instant recordedAt) {
        if (type == null || type == TreatmentEntryType.CORRECTION) {
            throw new AdmissionRuleViolationException("INPATIENT_INVALID_STATUS_TRANSITION",
                    "A new treatment entry must use a non-correction type");
        }
        return new TreatmentEntry(UUID.randomUUID(), Objects.requireNonNull(admissionId), type,
                requireText(content), Objects.requireNonNull(authoredBy), Objects.requireNonNull(recordedAt), null);
    }

    public static TreatmentEntry correct(TreatmentEntry original, String content,
                                         UUID authoredBy, Instant recordedAt) {
        return correctForAdmission(original == null ? null : original.maDotNoiTru(), original,
                content, authoredBy, recordedAt);
    }

    public static TreatmentEntry correctForAdmission(UUID admissionId, TreatmentEntry original,
                                                     String content, UUID authoredBy, Instant recordedAt) {
        if (original == null || admissionId == null || !admissionId.equals(original.maDotNoiTru())
                || original.loaiMuc() == TreatmentEntryType.CORRECTION) {
            throw new AdmissionRuleViolationException("INPATIENT_INVALID_STATUS_TRANSITION",
                    "Correction must target an original entry in the same admission");
        }
        return new TreatmentEntry(UUID.randomUUID(), admissionId, TreatmentEntryType.CORRECTION,
                requireText(content), Objects.requireNonNull(authoredBy), Objects.requireNonNull(recordedAt),
                original.maMucDienBien());
    }

    private static String requireText(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Treatment content is required");
        }
        return content;
    }

    public UUID entryId() { return maMucDienBien; }
    public UUID admissionId() { return maDotNoiTru; }
    public TreatmentEntryType entryType() { return loaiMuc; }
    public String content() { return noiDung; }
    public UUID authoredBy() { return nguoiGhi; }
    public Instant recordedAt() { return thoiGianGhi; }
    public UUID correctionOfEntryId() { return maMucBiDinhChinh; }
}
