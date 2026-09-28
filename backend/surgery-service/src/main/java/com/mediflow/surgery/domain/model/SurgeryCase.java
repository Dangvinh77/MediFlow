package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Surgery aggregate root. Persistence and cross-service checks are performed outside the domain. */
public final class SurgeryCase {

    private static final int MAX_PROCEDURE_CODE_LENGTH = 64;
    private static final int MAX_INDICATION_LENGTH = 4_000;
    private static final int MAX_REASON_LENGTH = 1_000;

    private final UUID maCaPhauThuat;
    private final UUID maYeuCauPhauThuat;
    private final CareEpisode dotDieuTri;
    private final UUID maBenhNhan;
    private final UUID maKhoa;
    private final UUID nguoiYeuCau;
    private final String maThuThuat;
    private final String chiDinh;
    private final SurgeryPriority mucDoUuTien;
    private final Instant thoiDiemYeuCau;
    private final List<SurgeryStateChange> lichSuTrangThai = new ArrayList<>();

    private SurgeryStatus trangThai;
    private ReadinessSnapshot anhChupSanSang;
    private Instant thoiDiemSanSang;
    private Instant thoiDiemBatDau;
    private Instant thoiDiemHoanTat;
    private Instant thoiDiemHuy;
    private UUID nguoiHuy;
    private String lyDoHuy;
    private long phienBan;

    private SurgeryCase(
            UUID maCaPhauThuat,
            UUID maYeuCauPhauThuat,
            CareEpisode dotDieuTri,
            UUID maBenhNhan,
            UUID maKhoa,
            UUID nguoiYeuCau,
            String maThuThuat,
            String chiDinh,
            SurgeryPriority mucDoUuTien,
            Instant thoiDiemYeuCau) {
        this.maCaPhauThuat = required(maCaPhauThuat, "SURGERY_CASE_ID_REQUIRED");
        this.maYeuCauPhauThuat = required(maYeuCauPhauThuat, "SURGERY_REQUEST_ID_REQUIRED");
        this.dotDieuTri = required(dotDieuTri, "SURGERY_EPISODE_REQUIRED");
        this.maBenhNhan = required(maBenhNhan, "SURGERY_PATIENT_ID_REQUIRED");
        this.maKhoa = required(maKhoa, "SURGERY_DEPARTMENT_ID_REQUIRED");
        this.nguoiYeuCau = required(nguoiYeuCau, "SURGERY_REQUESTER_REQUIRED");
        this.maThuThuat = requiredText(
                maThuThuat, MAX_PROCEDURE_CODE_LENGTH, "SURGERY_PROCEDURE_CODE_INVALID");
        this.chiDinh = requiredText(chiDinh, MAX_INDICATION_LENGTH, "SURGERY_INDICATION_INVALID");
        this.mucDoUuTien = required(mucDoUuTien, "SURGERY_PRIORITY_REQUIRED");
        this.thoiDiemYeuCau = required(thoiDiemYeuCau, "SURGERY_REQUEST_TIME_REQUIRED");
        this.trangThai = SurgeryStatus.REQUESTED;
        this.lichSuTrangThai.add(new SurgeryStateChange(
                null, SurgeryStatus.REQUESTED, nguoiYeuCau, "CASE_CREATED", thoiDiemYeuCau));
    }

    public static SurgeryCase create(
            UUID surgeryCaseId,
            UUID surgeryRequestId,
            CareEpisode careEpisode,
            UUID patientId,
            UUID departmentId,
            UUID requestedBy,
            String procedureCode,
            String indication,
            SurgeryPriority priority,
            Instant requestedAt) {
        return new SurgeryCase(
                surgeryCaseId,
                surgeryRequestId,
                careEpisode,
                patientId,
                departmentId,
                requestedBy,
                procedureCode,
                indication,
                priority,
                requestedAt);
    }

    public void beginPreop(UUID actorId, Instant at) {
        transition(SurgeryStatus.REQUESTED, SurgeryStatus.PREOP_IN_PROGRESS,
                actorId, "PREOP_STARTED", at);
    }

    public void markReady(ReadinessSnapshot snapshot, UUID actorId) {
        requireStatus(SurgeryStatus.PREOP_IN_PROGRESS);
        requireSnapshotForThisCase(snapshot);
        if (!snapshot.isReady()) {
            throw rule("SURGERY_NOT_READY", "Ca phẫu thuật chưa đạt đầy đủ readiness guards");
        }
        UUID actor = required(actorId, "SURGERY_ACTOR_REQUIRED");
        transition(SurgeryStatus.PREOP_IN_PROGRESS, SurgeryStatus.READY,
                actor, "ALL_READINESS_GUARDS_SATISFIED", snapshot.evaluatedAt());
        this.anhChupSanSang = snapshot;
        this.thoiDiemSanSang = snapshot.evaluatedAt();
    }

    /** Finalization occurs only after the application has atomically reserved room/team resources. */
    public void finalizeSchedule(UUID actorId, Instant at) {
        requireStatus(SurgeryStatus.READY);
        if (anhChupSanSang == null || !anhChupSanSang.isReady()) {
            throw rule("SURGERY_NOT_READY", "Không thể chốt lịch khi readiness snapshot không hợp lệ");
        }
        transition(SurgeryStatus.READY, SurgeryStatus.SCHEDULED,
                actorId, "SCHEDULE_FINALIZED", at);
    }

    /** START rechecks the complete guard set; a prior READY snapshot alone is never sufficient. */
    public void start(ReadinessSnapshot currentSnapshot, UUID actorId, Instant at) {
        requireStatus(SurgeryStatus.SCHEDULED);
        requireSnapshotForThisCase(currentSnapshot);
        if (!currentSnapshot.isReady()) {
            throw rule("SURGERY_NOT_READY", "Readiness đã thay đổi trước thời điểm bắt đầu");
        }
        if (at == null || at.isBefore(thoiDiemSanSang)) {
            throw rule("SURGERY_INVALID_TIME", "Thời điểm bắt đầu không hợp lệ");
        }
        if (currentSnapshot.evaluatedAt().isAfter(at)) {
            throw rule("SURGERY_INVALID_TIME", "Readiness phải được đánh giá trước khi bắt đầu");
        }
        UUID actor = required(actorId, "SURGERY_ACTOR_REQUIRED");
        transition(SurgeryStatus.SCHEDULED, SurgeryStatus.IN_PROGRESS,
                actor, "SURGERY_STARTED", at);
        this.anhChupSanSang = currentSnapshot;
        this.thoiDiemBatDau = at;
    }

    public void complete(UUID actorId, Instant at) {
        requireStatus(SurgeryStatus.IN_PROGRESS);
        if (at == null || thoiDiemBatDau == null || !at.isAfter(thoiDiemBatDau)) {
            throw rule("SURGERY_INVALID_TIME", "Thời điểm hoàn tất phải sau thời điểm bắt đầu");
        }
        UUID actor = required(actorId, "SURGERY_ACTOR_REQUIRED");
        transition(SurgeryStatus.IN_PROGRESS, SurgeryStatus.COMPLETED,
                actor, "SURGERY_COMPLETED", at);
        this.thoiDiemHoanTat = at;
    }

    /**
     * Guard-changing mutations invalidate READY/SCHEDULED and require a fresh evaluation.
     * The application transaction must release any finalized resource reservation with this change.
     */
    public void invalidateReadiness(UUID actorId, Instant at, String reason) {
        if (trangThai != SurgeryStatus.READY && trangThai != SurgeryStatus.SCHEDULED) {
            throw invalidTransition("Chỉ có thể vô hiệu hóa readiness trước khi bắt đầu ca mổ");
        }
        String validReason = requiredText(reason, MAX_REASON_LENGTH, "SURGERY_INVALIDATION_REASON_REQUIRED");
        UUID actor = required(actorId, "SURGERY_ACTOR_REQUIRED");
        Instant invalidatedAt = required(at, "SURGERY_TRANSITION_TIME_REQUIRED");
        transition(trangThai, SurgeryStatus.PREOP_IN_PROGRESS,
                actor, validReason, invalidatedAt);
        this.anhChupSanSang = null;
        this.thoiDiemSanSang = null;
    }

    /** V1 cancellation is allowed only before IN_PROGRESS; the stage is derived from persisted state. */
    public void cancel(UUID actorId, Instant at, String reason) {
        if (trangThai == SurgeryStatus.IN_PROGRESS
                || trangThai == SurgeryStatus.COMPLETED
                || trangThai == SurgeryStatus.CANCELLED) {
            throw invalidTransition("V1 chỉ cho phép hủy ca trước khi bắt đầu phẫu thuật");
        }
        String validReason = requiredText(reason, MAX_REASON_LENGTH, "SURGERY_CANCELLATION_REASON_REQUIRED");
        UUID actor = required(actorId, "SURGERY_ACTOR_REQUIRED");
        Instant cancelledAt = required(at, "SURGERY_CANCELLATION_TIME_REQUIRED");
        transition(trangThai, SurgeryStatus.CANCELLED, actor, validReason, cancelledAt);
        this.nguoiHuy = actor;
        this.lyDoHuy = validReason;
        this.thoiDiemHuy = cancelledAt;
    }

    private void transition(
            SurgeryStatus expected,
            SurgeryStatus next,
            UUID actorId,
            String reason,
            Instant at) {
        requireStatus(expected);
        UUID actor = required(actorId, "SURGERY_ACTOR_REQUIRED");
        Instant changedAt = required(at, "SURGERY_TRANSITION_TIME_REQUIRED");
        if (!lichSuTrangThai.isEmpty()
                && changedAt.isBefore(lichSuTrangThai.get(lichSuTrangThai.size() - 1).thoiDiem())) {
            throw rule("SURGERY_INVALID_TIME", "Thời điểm chuyển trạng thái không được đi lùi");
        }
        SurgeryStatus previous = trangThai;
        trangThai = next;
        phienBan++;
        lichSuTrangThai.add(new SurgeryStateChange(previous, next, actor, reason, changedAt));
    }

    private void requireSnapshotForThisCase(ReadinessSnapshot snapshot) {
        if (snapshot == null || !maCaPhauThuat.equals(snapshot.surgeryCaseId())) {
            throw rule("SURGERY_READINESS_SNAPSHOT_MISMATCH",
                    "Readiness snapshot không thuộc ca phẫu thuật này");
        }
    }

    private void requireStatus(SurgeryStatus expected) {
        if (trangThai != expected) {
            throw invalidTransition("Trạng thái hiện tại không cho phép thao tác này");
        }
    }

    private static <T> T required(T value, String code) {
        if (value == null) {
            throw rule(code, "Dữ liệu bắt buộc của ca phẫu thuật bị thiếu");
        }
        return value;
    }

    private static String requiredText(String value, int maxLength, String code) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw rule(code, "Giá trị bắt buộc bị trống hoặc vượt độ dài cho phép");
        }
        return value.trim();
    }

    private static SurgeryRuleException invalidTransition(String message) {
        return rule("SURGERY_INVALID_TRANSITION", message);
    }

    private static SurgeryRuleException rule(String code, String message) {
        return new SurgeryRuleException(code, message);
    }

    public UUID getMaCaPhauThuat() { return maCaPhauThuat; }
    public UUID getMaYeuCauPhauThuat() { return maYeuCauPhauThuat; }
    public CareEpisode getDotDieuTri() { return dotDieuTri; }
    public UUID getMaBenhNhan() { return maBenhNhan; }
    public UUID getMaKhoa() { return maKhoa; }
    public UUID getNguoiYeuCau() { return nguoiYeuCau; }
    public String getMaThuThuat() { return maThuThuat; }
    public String getChiDinh() { return chiDinh; }
    public SurgeryPriority getMucDoUuTien() { return mucDoUuTien; }
    public SurgeryStatus getTrangThai() { return trangThai; }
    public ReadinessSnapshot getAnhChupSanSang() { return anhChupSanSang; }
    public Instant getThoiDiemYeuCau() { return thoiDiemYeuCau; }
    public Instant getThoiDiemSanSang() { return thoiDiemSanSang; }
    public Instant getThoiDiemBatDau() { return thoiDiemBatDau; }
    public Instant getThoiDiemHoanTat() { return thoiDiemHoanTat; }
    public Instant getThoiDiemHuy() { return thoiDiemHuy; }
    public UUID getNguoiHuy() { return nguoiHuy; }
    public String getLyDoHuy() { return lyDoHuy; }
    public long getPhienBan() { return phienBan; }
    public List<SurgeryStateChange> getLichSuTrangThai() { return List.copyOf(lichSuTrangThai); }
}
