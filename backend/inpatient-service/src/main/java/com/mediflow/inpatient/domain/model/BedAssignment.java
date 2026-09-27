package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.domain.model.enums.BedAssignmentStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class BedAssignment {

    private final UUID maPhanGiuong;
    private final UUID maDotNoiTru;
    private final UUID maGiuong;
    private final UUID nguoiPhanGiuong;
    private final Instant thoiGianPhanGiuong;
    private BedAssignmentStatus status;
    private UUID nguoiTraGiuong;
    private Instant thoiGianTraGiuong;
    private String lyDoTraGiuong;

    private BedAssignment(UUID maPhanGiuong, UUID maDotNoiTru, UUID maGiuong, UUID nguoiPhanGiuong,
                          Instant thoiGianPhanGiuong, BedAssignmentStatus status,
                          UUID nguoiTraGiuong, Instant thoiGianTraGiuong, String lyDoTraGiuong) {
        this.maPhanGiuong = Objects.requireNonNull(maPhanGiuong);
        this.maDotNoiTru = Objects.requireNonNull(maDotNoiTru);
        this.maGiuong = Objects.requireNonNull(maGiuong);
        this.nguoiPhanGiuong = Objects.requireNonNull(nguoiPhanGiuong);
        this.thoiGianPhanGiuong = Objects.requireNonNull(thoiGianPhanGiuong);
        this.status = Objects.requireNonNull(status);
        this.nguoiTraGiuong = nguoiTraGiuong;
        this.thoiGianTraGiuong = thoiGianTraGiuong;
        this.lyDoTraGiuong = lyDoTraGiuong;
    }

    public static BedAssignment create(UUID id, UUID admissionId, UUID bedId,
                                        UUID assignedBy, Instant assignedAt) {
        return new BedAssignment(id, admissionId, bedId, assignedBy, assignedAt,
                BedAssignmentStatus.ACTIVE, null, null, null);
    }

    public static BedAssignment restore(UUID id, UUID admissionId, UUID bedId,
                                        UUID assignedBy, Instant assignedAt,
                                        BedAssignmentStatus status, UUID releasedBy,
                                        Instant releasedAt, String releaseReason) {
        return new BedAssignment(id, admissionId, bedId, assignedBy, assignedAt,
                status, releasedBy, releasedAt, releaseReason);
    }

    public BedAssignment release(UUID releasedBy, String reason, Instant releasedAt) {
        if (status != BedAssignmentStatus.ACTIVE) {
            throw new AdmissionRuleViolationException("INPATIENT_INVALID_STATUS_TRANSITION",
                    "Only an active bed assignment can be released");
        }
        if (releasedBy == null || reason == null || reason.isBlank() || releasedAt == null
                || releasedAt.isBefore(thoiGianPhanGiuong)) {
            throw new AdmissionRuleViolationException("INPATIENT_INVALID_STATUS_TRANSITION",
                    "Bed release audit is invalid");
        }
        this.nguoiTraGiuong = releasedBy;
        this.lyDoTraGiuong = reason;
        this.thoiGianTraGiuong = releasedAt;
        this.status = BedAssignmentStatus.RELEASED;
        return this;
    }

    public UUID assignmentId() { return maPhanGiuong; }
    public UUID admissionId() { return maDotNoiTru; }
    public UUID bedId() { return maGiuong; }
    public UUID assignedBy() { return nguoiPhanGiuong; }
    public Instant assignedAt() { return thoiGianPhanGiuong; }
    public BedAssignmentStatus status() { return status; }
    public UUID releasedBy() { return nguoiTraGiuong; }
    public Instant releasedAt() { return thoiGianTraGiuong; }
    public String releaseReason() { return lyDoTraGiuong; }
}
