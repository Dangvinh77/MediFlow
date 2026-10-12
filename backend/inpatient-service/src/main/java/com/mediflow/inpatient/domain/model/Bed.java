package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.domain.model.enums.BedStatus;

import java.util.Objects;
import java.util.UUID;

public final class Bed {

    private final UUID maGiuong;
    private final UUID maKhoa;
    private final String maKhu;
    private final String maPhong;
    private final String maGiuongTrongPhong;
    private String loaiGiuong;
    private BedStatus status;
    private boolean active;
    private long phienBanCongSuat;

    private Bed(UUID maGiuong, UUID maKhoa, String maKhu, String maPhong, String maGiuongTrongPhong,
                String loaiGiuong, BedStatus status, boolean active, long phienBanCongSuat) {
        this.maGiuong = Objects.requireNonNull(maGiuong);
        this.maKhoa = Objects.requireNonNull(maKhoa);
        this.maKhu = requireText(maKhu);
        this.maPhong = requireText(maPhong);
        this.maGiuongTrongPhong = requireText(maGiuongTrongPhong);
        this.loaiGiuong = requireText(loaiGiuong);
        this.status = Objects.requireNonNull(status);
        this.active = active;
        if (phienBanCongSuat < 0) {
            throw new IllegalArgumentException("capacityRevision must not be negative");
        }
        this.phienBanCongSuat = phienBanCongSuat;
    }

    public static Bed create(UUID id, UUID departmentId, String wardCode, String roomCode,
                             String bedCode, String bedType) {
        return new Bed(id, departmentId, wardCode, roomCode, bedCode, bedType,
                BedStatus.AVAILABLE, true, 0);
    }

    public static Bed restore(UUID id, UUID departmentId, String wardCode, String roomCode,
                              String bedCode, String bedType, BedStatus status, boolean active) {
        return restore(id, departmentId, wardCode, roomCode, bedCode, bedType, status, active, 0);
    }

    public static Bed restore(UUID id, UUID departmentId, String wardCode, String roomCode,
                              String bedCode, String bedType, BedStatus status, boolean active,
                              long capacityRevision) {
        return new Bed(id, departmentId, wardCode, roomCode, bedCode, bedType, status, active,
                capacityRevision);
    }

    public Bed assign() {
        if (!active || status != BedStatus.AVAILABLE) {
            throw new AdmissionRuleViolationException("INPATIENT_BED_UNAVAILABLE",
                    "Bed is inactive or unavailable");
        }
        status = BedStatus.OCCUPIED;
        return this;
    }

    public Bed release() {
        if (status != BedStatus.OCCUPIED) {
            throw new AdmissionRuleViolationException("INPATIENT_BED_UNAVAILABLE",
                    "Only an occupied bed can be released");
        }
        status = BedStatus.AVAILABLE;
        return this;
    }

    public Bed update(String bedType, BedStatus requestedStatus, boolean active, boolean hasActiveAssignment) {
        BedStatus targetStatus = Objects.requireNonNull(requestedStatus);
        if (hasActiveAssignment && (targetStatus != BedStatus.OCCUPIED || !active)) {
            throw new AdmissionRuleViolationException("INPATIENT_BED_UNAVAILABLE",
                    "An occupied bed cannot be deactivated or marked available");
        }
        if (!hasActiveAssignment && targetStatus == BedStatus.OCCUPIED) {
            throw new AdmissionRuleViolationException("INPATIENT_BED_UNAVAILABLE",
                    "Bed occupancy is controlled by bed assignment");
        }
        this.loaiGiuong = requireText(bedType);
        this.status = targetStatus;
        this.active = active;
        return this;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Bed code and type values are required");
        }
        return value;
    }

    public UUID bedId() { return maGiuong; }
    public UUID departmentId() { return maKhoa; }
    public String wardCode() { return maKhu; }
    public String roomCode() { return maPhong; }
    public String bedCode() { return maGiuongTrongPhong; }
    public String bedType() { return loaiGiuong; }
    public BedStatus status() { return status; }
    public boolean active() { return active; }
    public long capacityRevision() { return phienBanCongSuat; }
    public long nextCapacityRevision() {
        phienBanCongSuat = Math.addExact(phienBanCongSuat, 1);
        return phienBanCongSuat;
    }
}
