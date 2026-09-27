package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import com.mediflow.inpatient.domain.model.enums.OverrideType;
import com.mediflow.inpatient.domain.model.enums.SettlementOutcome;

import java.time.Instant;
import java.util.UUID;

/** Admission aggregate; application workflows persist each successful transition atomically. */
public final class Admission {

    private final UUID maDotNoiTru;
    private final UUID maYeuCauNoiTru;
    private final UUID maBenhNhan;
    private final UUID maHoSoNguon;
    private final UUID nguoiYeuCau;
    private final String tomTatChanDoan;
    private final Instant thoiGianYeuCau;
    private final UUID maKhoa;
    private final AdmissionPriority doUuTien;
    private final boolean capCuu;

    private AdmissionStatus status;
    private Instant thoiGianYeuCauTamUng;
    private UUID maXacNhanTamUng;
    private Instant hanXacNhanTamUng;
    private UUID maPheDuyetCapCuu;
    private UUID maQuyetToan;
    private UUID maPheDuyetDong;
    private UUID maTomTatRaVien;
    private Instant thoiGianNhapVien;
    private Instant thoiGianRaVienYTe;
    private Instant thoiGianDong;
    private Instant thoiGianHuy;
    private String lyDoHuy;

    private Admission(
            UUID maDotNoiTru,
            UUID maYeuCauNoiTru,
            UUID maBenhNhan,
            UUID maHoSoNguon,
            UUID nguoiYeuCau,
            String tomTatChanDoan,
            Instant thoiGianYeuCau,
            UUID maKhoa,
            AdmissionPriority doUuTien,
            boolean capCuu,
            AdmissionStatus status,
            Instant thoiGianYeuCauTamUng,
            UUID maXacNhanTamUng,
            Instant hanXacNhanTamUng,
            UUID maPheDuyetCapCuu,
            UUID maQuyetToan,
            UUID maPheDuyetDong,
            UUID maTomTatRaVien,
            Instant thoiGianNhapVien,
            Instant thoiGianRaVienYTe,
            Instant thoiGianDong,
            Instant thoiGianHuy,
            String lyDoHuy) {
        this.maDotNoiTru = require(maDotNoiTru, "admissionId");
        this.maYeuCauNoiTru = require(maYeuCauNoiTru, "admissionRequestId");
        this.maBenhNhan = require(maBenhNhan, "patientId");
        this.maHoSoNguon = require(maHoSoNguon, "sourceRecordId");
        this.nguoiYeuCau = require(nguoiYeuCau, "requestedBy");
        this.tomTatChanDoan = requireText(tomTatChanDoan, "diagnosisSummary");
        this.thoiGianYeuCau = require(thoiGianYeuCau, "requestedAt");
        this.maKhoa = require(maKhoa, "departmentId");
        this.doUuTien = require(doUuTien, "priority");
        this.capCuu = capCuu;
        this.status = require(status, "status");
        this.thoiGianYeuCauTamUng = thoiGianYeuCauTamUng;
        this.maXacNhanTamUng = maXacNhanTamUng;
        this.hanXacNhanTamUng = hanXacNhanTamUng;
        this.maPheDuyetCapCuu = maPheDuyetCapCuu;
        this.maQuyetToan = maQuyetToan;
        this.maPheDuyetDong = maPheDuyetDong;
        this.maTomTatRaVien = maTomTatRaVien;
        this.thoiGianNhapVien = thoiGianNhapVien;
        this.thoiGianRaVienYTe = thoiGianRaVienYTe;
        this.thoiGianDong = thoiGianDong;
        this.thoiGianHuy = thoiGianHuy;
        this.lyDoHuy = lyDoHuy;
    }

    public static Admission create(
            UUID admissionId,
            UUID admissionRequestId,
            UUID patientId,
            UUID sourceRecordId,
            UUID requestedBy,
            String diagnosisSummary,
            Instant requestedAt,
            UUID departmentId,
            AdmissionPriority priority,
            boolean emergency) {
        return new Admission(admissionId, admissionRequestId, patientId, sourceRecordId, requestedBy,
                diagnosisSummary, requestedAt, departmentId, priority, emergency,
                AdmissionStatus.REQUESTED, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public static Admission restore(
            UUID admissionId,
            UUID admissionRequestId,
            UUID patientId,
            UUID sourceRecordId,
            UUID requestedBy,
            String diagnosisSummary,
            Instant requestedAt,
            UUID departmentId,
            AdmissionPriority priority,
            boolean emergency,
            AdmissionStatus status,
            Instant depositRequestedAt,
            UUID depositClearanceId,
            Instant depositExpiresAt,
            UUID emergencyOverrideId,
            UUID settlementId,
            UUID closeOverrideId,
            UUID dischargeSummaryId,
            Instant admittedAt,
            Instant medicallyDischargedAt,
            Instant closedAt,
            Instant cancelledAt,
            String cancellationReason) {
        return new Admission(admissionId, admissionRequestId, patientId, sourceRecordId, requestedBy,
                diagnosisSummary, requestedAt, departmentId, priority, emergency, status,
                depositRequestedAt, depositClearanceId, depositExpiresAt, emergencyOverrideId, settlementId,
                closeOverrideId, dischargeSummaryId, admittedAt, medicallyDischargedAt,
                closedAt, cancelledAt, cancellationReason);
    }

    public boolean markDepositRequested(Instant at) {
        if (thoiGianYeuCauTamUng != null) {
            return false;
        }
        thoiGianYeuCauTamUng = require(at, "depositRequestedAt");
        return true;
    }

    public Admission markAwaitingBed() {
        requireStatus(AdmissionStatus.REQUESTED);
        status = AdmissionStatus.AWAITING_BED;
        return this;
    }

    public Admission applyBedAssignment(boolean activeBed, Instant now) {
        requireBeforeMedicalDischarge();
        recalculateReadiness(activeBed, now);
        return this;
    }

    public Admission applyFinancialClearance(UUID clearanceId, Instant expiresAt,
                                             boolean activeBed, Instant now) {
        requireStatusBeforeAdmission();
        this.maXacNhanTamUng = require(clearanceId, "clearanceId");
        this.hanXacNhanTamUng = expiresAt;
        recalculateReadiness(activeBed, now);
        return this;
    }

    public Admission refreshReadiness(boolean activeBed, Instant now) {
        requireStatusBeforeAdmission();
        recalculateReadiness(activeBed, now);
        return this;
    }

    public Admission admit(Instant at, boolean activeBed, EmergencyOverride emergencyOverride) {
        requireBeforeMedicalDischarge();
        require(at, "admittedAt");
        if (!activeBed) {
            throw violation("INPATIENT_ACTIVE_BED_REQUIRED", "An active bed assignment is required");
        }
        if (emergencyOverride != null) {
            if (!capCuu) {
                throw violation("INPATIENT_EMERGENCY_OVERRIDE_INVALID",
                        "Emergency override is only allowed for an emergency admission");
            }
            if (emergencyOverride.thoiGianDuyet().isAfter(at)) {
                throw violation("INPATIENT_EMERGENCY_OVERRIDE_INVALID",
                        "Emergency override approval cannot be in the future");
            }
            maPheDuyetCapCuu = emergencyOverride.maPheDuyet();
        } else {
            if (status != AdmissionStatus.READY || !hasValidClearance(at)) {
                throw violation("INPATIENT_DEPOSIT_CLEARANCE_REQUIRED",
                        "A valid admission deposit clearance is required");
            }
        }
        status = AdmissionStatus.ADMITTED;
        thoiGianNhapVien = at;
        return this;
    }

    public Admission cancel(UUID cancelledBy, String reason, Instant at) {
        require(cancelledBy, "cancelledBy");
        if (reason == null || reason.isBlank()) {
            throw violation("INPATIENT_INVALID_STATUS_TRANSITION", "Cancellation reason is required");
        }
        requireStatusBeforeAdmission();
        thoiGianHuy = require(at, "cancelledAt");
        lyDoHuy = reason;
        status = AdmissionStatus.CANCELLED;
        return this;
    }

    public Admission medicallyDischarge(UUID summaryId, Instant at) {
        requireStatus(AdmissionStatus.ADMITTED);
        maTomTatRaVien = require(summaryId, "summaryId");
        thoiGianRaVienYTe = require(at, "approvedAt");
        status = AdmissionStatus.MEDICALLY_DISCHARGED;
        return this;
    }

    public Admission acceptSettlement(UUID settlementId, SettlementOutcome outcome) {
        requireStatus(AdmissionStatus.MEDICALLY_DISCHARGED);
        if (settlementId == null || outcome == null) {
            throw violation("INPATIENT_SETTLEMENT_REQUIRED", "Settlement identity and outcome are required");
        }
        if (isCloseable(outcome)) {
            maQuyetToan = settlementId;
        }
        return this;
    }

    public Admission close(boolean activeBed, SettlementOutcome outcome, UUID settlementId,
                           CloseOverride override, Instant at) {
        requireStatus(AdmissionStatus.MEDICALLY_DISCHARGED);
        if (activeBed) {
            throw violation("INPATIENT_ACTIVE_BED_MUST_BE_RELEASED",
                    "The active bed must be released before administrative close");
        }
        if (override != null) {
            if (override.loaiPheDuyet() != OverrideType.DEBT_CLOSE
                    && override.loaiPheDuyet() != OverrideType.WAIVER_CLOSE) {
                throw violation("INPATIENT_SETTLEMENT_REQUIRED", "Close override type is invalid");
            }
            if (override.thoiGianDuyet().isAfter(at)) {
                throw violation("INPATIENT_SETTLEMENT_REQUIRED", "Close approval cannot be in the future");
            }
            maPheDuyetDong = override.maPheDuyet();
        } else if (!isCloseable(outcome) || settlementId == null) {
            throw violation("INPATIENT_SETTLEMENT_REQUIRED",
                    "A final settlement or audited close override is required");
        } else {
            maQuyetToan = settlementId;
        }
        thoiGianDong = require(at, "closedAt");
        status = AdmissionStatus.CLOSED;
        return this;
    }

    private void recalculateReadiness(boolean activeBed, Instant now) {
        if (status == AdmissionStatus.CANCELLED) {
            throw violation("INPATIENT_INVALID_STATUS_TRANSITION", "Cancelled admission cannot change readiness");
        }
        if (hasValidClearance(now)) {
            status = activeBed ? AdmissionStatus.READY : AdmissionStatus.AWAITING_BED;
        } else {
            status = activeBed ? AdmissionStatus.AWAITING_DEPOSIT : AdmissionStatus.AWAITING_BED;
        }
    }

    private boolean hasValidClearance(Instant now) {
        return maXacNhanTamUng != null
                && (hanXacNhanTamUng == null || hanXacNhanTamUng.isAfter(now));
    }

    private static boolean isCloseable(SettlementOutcome outcome) {
        return outcome == SettlementOutcome.PAID_IN_FULL
                || outcome == SettlementOutcome.DEBT_APPROVED
                || outcome == SettlementOutcome.WAIVED;
    }

    private void requireBeforeMedicalDischarge() {
        requireStatusBeforeAdmission();
    }

    private void requireStatusBeforeAdmission() {
        if (status != AdmissionStatus.REQUESTED && status != AdmissionStatus.AWAITING_BED
                && status != AdmissionStatus.AWAITING_DEPOSIT && status != AdmissionStatus.READY) {
            throw violation("INPATIENT_INVALID_STATUS_TRANSITION", "Admission is no longer awaiting admission");
        }
    }

    private void requireStatus(AdmissionStatus expected) {
        if (status != expected) {
            throw violation("INPATIENT_INVALID_STATUS_TRANSITION",
                    "Expected " + expected + " but admission is " + status);
        }
    }

    private static AdmissionRuleViolationException violation(String code, String message) {
        return new AdmissionRuleViolationException(code, message);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    public UUID admissionId() { return maDotNoiTru; }
    public UUID admissionRequestId() { return maYeuCauNoiTru; }
    public UUID patientId() { return maBenhNhan; }
    public UUID sourceRecordId() { return maHoSoNguon; }
    public UUID requestedBy() { return nguoiYeuCau; }
    public String diagnosisSummary() { return tomTatChanDoan; }
    public Instant requestedAt() { return thoiGianYeuCau; }
    public UUID departmentId() { return maKhoa; }
    public AdmissionPriority priority() { return doUuTien; }
    public boolean emergency() { return capCuu; }
    public AdmissionStatus status() { return status; }
    public Instant depositRequestedAt() { return thoiGianYeuCauTamUng; }
    public UUID depositClearanceId() { return maXacNhanTamUng; }
    public Instant depositExpiresAt() { return hanXacNhanTamUng; }
    public UUID emergencyOverrideId() { return maPheDuyetCapCuu; }
    public UUID settlementId() { return maQuyetToan; }
    public UUID closeOverrideId() { return maPheDuyetDong; }
    public UUID dischargeSummaryId() { return maTomTatRaVien; }
    public Instant admittedAt() { return thoiGianNhapVien; }
    public Instant medicallyDischargedAt() { return thoiGianRaVienYTe; }
    public Instant closedAt() { return thoiGianDong; }
    public Instant cancelledAt() { return thoiGianHuy; }
    public String cancellationReason() { return lyDoHuy; }
}
