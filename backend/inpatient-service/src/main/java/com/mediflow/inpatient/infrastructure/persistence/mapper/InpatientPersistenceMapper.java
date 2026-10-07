package com.mediflow.inpatient.infrastructure.persistence.mapper;

import com.mediflow.inpatient.domain.model.Admission;
import com.mediflow.inpatient.domain.model.AdmissionStatusHistory;
import com.mediflow.inpatient.domain.model.Bed;
import com.mediflow.inpatient.domain.model.BedAssignment;
import com.mediflow.inpatient.domain.model.ClinicalOrderReference;
import com.mediflow.inpatient.domain.model.DepositTopupRequest;
import com.mediflow.inpatient.domain.model.DischargeSummary;
import com.mediflow.inpatient.domain.model.FinancialClearance;
import com.mediflow.inpatient.domain.model.SettlementSnapshot;
import com.mediflow.inpatient.domain.model.SurgeryEventReceipt;
import com.mediflow.inpatient.domain.model.TreatmentEntry;
import com.mediflow.inpatient.domain.model.enums.OverrideType;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.AdmissionJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.AdmissionStatusHistoryJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.BedAssignmentJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.BedJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.ClinicalOrderReferenceJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.DepositTopupRequestJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.DischargeSummaryJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.FinancialClearanceJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.OverrideJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.SettlementSnapshotJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.SurgeryEventReceiptJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.TreatmentEntryJpaEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Explicit conversions between the inpatient domain and its database rows. */
@Component
public class InpatientPersistenceMapper {

    public SurgeryEventReceipt toDomain(SurgeryEventReceiptJpaEntity row) {
        return SurgeryEventReceipt.restore(row.maTiepNhan, row.maSuKien, row.loaiSuKien,
                row.maNghiepVu, row.dauVanTai, row.maCaMo, row.maYeuCauMo,
                row.maDotNoiTru, row.maBenhNhan,
                row.maKhoa, row.phienBanCa, row.phienBanNguon, row.trangThaiDich, row.tomTat,
                row.noiDungDienBien, row.thoiGianDienBien, row.nhanLuc, row.apDungLuc);
    }

    public SurgeryEventReceiptJpaEntity copy(SurgeryEventReceipt model,
                                              SurgeryEventReceiptJpaEntity row) {
        row.maTiepNhan = model.receiptId();
        row.maSuKien = model.eventId();
        row.loaiSuKien = model.eventType();
        row.maNghiepVu = model.operationId();
        row.dauVanTai = model.payloadFingerprint();
        row.maCaMo = model.surgeryCaseId();
        row.maYeuCauMo = model.surgeryRequestId();
        row.maDotNoiTru = model.admissionId();
        row.maBenhNhan = model.patientId();
        row.maKhoa = model.departmentId();
        row.phienBanCa = model.caseRevision();
        row.phienBanNguon = model.sourceRevision();
        row.trangThaiDich = model.targetStatus();
        row.tomTat = model.summary();
        row.noiDungDienBien = model.timelineContent();
        row.thoiGianDienBien = model.timelineAt();
        row.nhanLuc = model.receivedAt();
        row.apDungLuc = model.appliedAt();
        return row;
    }

    public Admission toDomain(AdmissionJpaEntity row, Instant depositExpiresAt) {
        return Admission.restore(row.maDotNoiTru, row.maYeuCauNoiTru, row.maBenhNhan,
                row.maHoSoNguon, row.nguoiYeuCau, row.tomTatChanDoan, row.thoiGianYeuCau,
                row.maKhoa, row.doUuTien, row.capCuu, row.trangThai, row.thoiGianYeuCauTamUng,
                row.maXacNhanTamUng, depositExpiresAt, row.maPheDuyetCapCuu,
                row.maQuyetToan, row.maPheDuyetDong, row.maTomTatRaVien, row.thoiGianNhapVien,
                row.thoiGianRaVienYTe, row.thoiGianDong, row.thoiGianHuy, row.lyDoHuy);
    }

    public AdmissionJpaEntity copy(Admission model, AdmissionJpaEntity row) {
        row.maDotNoiTru = model.admissionId();
        row.maYeuCauNoiTru = model.admissionRequestId();
        row.maBenhNhan = model.patientId();
        row.maHoSoNguon = model.sourceRecordId();
        row.nguoiYeuCau = model.requestedBy();
        row.tomTatChanDoan = model.diagnosisSummary();
        row.thoiGianYeuCau = model.requestedAt();
        row.maKhoa = model.departmentId();
        row.doUuTien = model.priority();
        row.capCuu = model.emergency();
        row.trangThai = model.status();
        row.thoiGianYeuCauTamUng = model.depositRequestedAt();
        row.maXacNhanTamUng = model.depositClearanceId();
        row.maPheDuyetCapCuu = model.emergencyOverrideId();
        row.maQuyetToan = model.settlementId();
        row.maPheDuyetDong = model.closeOverrideId();
        row.maTomTatRaVien = model.dischargeSummaryId();
        row.thoiGianNhapVien = model.admittedAt();
        row.thoiGianRaVienYTe = model.medicallyDischargedAt();
        row.thoiGianDong = model.closedAt();
        row.thoiGianHuy = model.cancelledAt();
        row.lyDoHuy = model.cancellationReason();
        row.capNhatLuc = Instant.now();
        return row;
    }

    public Bed toDomain(BedJpaEntity row) {
        return Bed.restore(row.maGiuong, row.maKhoa, row.maKhu, row.maPhong,
                row.maGiuongTrongPhong, row.loaiGiuong, row.trangThai, row.dangHoatDong);
    }

    public BedJpaEntity copy(Bed model, BedJpaEntity row) {
        row.maGiuong = model.bedId();
        row.maKhoa = model.departmentId();
        row.maKhu = model.wardCode();
        row.maPhong = model.roomCode();
        row.maGiuongTrongPhong = model.bedCode();
        row.loaiGiuong = model.bedType();
        row.trangThai = model.status();
        row.dangHoatDong = model.active();
        row.capNhatLuc = Instant.now();
        return row;
    }

    public BedAssignment toDomain(BedAssignmentJpaEntity row) {
        return BedAssignment.restore(row.maPhanGiuong, row.maDotNoiTru, row.maGiuong,
                row.nguoiPhanGiuong, row.thoiGianPhanGiuong, row.trangThai,
                row.nguoiTraGiuong, row.thoiGianTraGiuong, row.lyDoTraGiuong);
    }

    public BedAssignmentJpaEntity copy(BedAssignment model, BedAssignmentJpaEntity row) {
        row.maPhanGiuong = model.assignmentId();
        row.maDotNoiTru = model.admissionId();
        row.maGiuong = model.bedId();
        row.nguoiPhanGiuong = model.assignedBy();
        row.thoiGianPhanGiuong = model.assignedAt();
        row.nguoiTraGiuong = model.releasedBy();
        row.thoiGianTraGiuong = model.releasedAt();
        row.lyDoTraGiuong = model.releaseReason();
        row.trangThai = model.status();
        return row;
    }

    public TreatmentEntry toDomain(TreatmentEntryJpaEntity row) {
        return new TreatmentEntry(row.maMucDienBien, row.maDotNoiTru, row.loaiMuc,
                row.noiDung, row.nguoiGhi, row.thoiGianGhi, row.maMucBiDinhChinh);
    }

    public TreatmentEntryJpaEntity copy(TreatmentEntry model, TreatmentEntryJpaEntity row) {
        row.maMucDienBien = model.entryId();
        row.maDotNoiTru = model.admissionId();
        row.loaiMuc = model.entryType();
        row.noiDung = model.content();
        row.nguoiGhi = model.authoredBy();
        row.thoiGianGhi = model.recordedAt();
        row.maMucBiDinhChinh = model.correctionOfEntryId();
        return row;
    }

    public ClinicalOrderReference toDomain(ClinicalOrderReferenceJpaEntity row) {
        return ClinicalOrderReference.restore(row.maThamChieu, row.maDotNoiTru,
                row.loaiYLenh, row.maYLenhBenNgoai, row.trangThai, row.tomTat, row.phienBanSuKien);
    }

    public ClinicalOrderReferenceJpaEntity copy(ClinicalOrderReference model,
                                                ClinicalOrderReferenceJpaEntity row) {
        row.maThamChieu = model.referenceId();
        row.maDotNoiTru = model.admissionId();
        row.loaiYLenh = model.orderType();
        row.maYLenhBenNgoai = model.externalOrderId();
        row.trangThai = model.status();
        row.tomTat = model.summary();
        row.phienBanSuKien = model.eventVersion();
        row.capNhatLuc = Instant.now();
        return row;
    }

    public DischargeSummaryJpaEntity copy(DischargeSummary model, DischargeSummaryJpaEntity row) {
        row.maTomTat = model.maTomTat();
        row.maDotNoiTru = model.maDotNoiTru();
        row.tomTatChanDoan = model.tomTatChanDoan();
        row.tomTatDieuTri = model.tomTatDieuTri();
        row.ketQua = model.ketQua();
        row.keHoachTheoDoi = model.keHoachTheoDoi();
        row.nguoiDuyet = model.nguoiDuyet();
        row.thoiGianDuyet = model.thoiGianDuyet();
        return row;
    }

    public FinancialClearanceJpaEntity copy(FinancialClearance model, FinancialClearanceJpaEntity row) {
        row.maXacNhan = model.maXacNhan();
        row.maSuKien = model.maSuKien();
        row.maHoaDon = model.maHoaDon();
        row.maTaiKhoan = model.maTaiKhoan();
        row.maDotNoiTru = model.maDotNoiTru();
        row.maBenhNhan = model.maBenhNhan();
        row.soTien = model.soTien();
        row.tienTe = model.tienTe();
        row.phuongThucThanhToan = model.phuongThucThanhToan();
        row.hetHanLuc = model.hetHanLuc();
        row.capCuuNgoaiLe = model.capCuuNgoaiLe();
        row.thoiGianCap = model.thoiGianCap();
        return row;
    }

    public SettlementSnapshotJpaEntity copy(SettlementSnapshot model, SettlementSnapshotJpaEntity row) {
        row.maQuyetToan = model.maQuyetToan();
        row.maSuKien = model.maSuKien();
        row.maDotNoiTru = model.maDotNoiTru();
        row.maTaiKhoan = model.maTaiKhoan();
        row.tongTien = model.tongTien();
        row.baoHiemThanhToan = model.baoHiemThanhToan();
        row.benhNhanPhaiTra = model.benhNhanPhaiTra();
        row.daThanhToan = model.daThanhToan();
        row.daHoanTien = model.daHoanTien();
        row.soDu = model.soDu();
        row.ketQua = model.ketQua();
        row.hoanTatLuc = model.hoanTatLuc();
        return row;
    }

    public SettlementSnapshot toDomain(SettlementSnapshotJpaEntity row) {
        return new SettlementSnapshot(row.maQuyetToan, row.maSuKien, row.maDotNoiTru,
                row.maTaiKhoan, row.tongTien, row.baoHiemThanhToan, row.benhNhanPhaiTra,
                row.daThanhToan, row.daHoanTien, row.soDu, row.ketQua, row.hoanTatLuc);
    }

    public DepositTopupRequestJpaEntity copy(DepositTopupRequest model, DepositTopupRequestJpaEntity row) {
        row.maYeuCauBoSung = model.maYeuCauBoSung();
        row.maSuKien = model.maSuKien();
        row.maDotNoiTru = model.maDotNoiTru();
        row.maTaiKhoan = model.maTaiKhoan();
        row.soDuHienTai = model.soDuHienTai();
        row.soTienYeuCau = model.soTienYeuCau();
        row.lyDo = model.lyDo();
        row.thoiGianYeuCau = model.thoiGianYeuCau();
        return row;
    }

    public AdmissionStatusHistoryJpaEntity copy(AdmissionStatusHistory model,
                                                AdmissionStatusHistoryJpaEntity row) {
        row.maLichSu = model.maLichSu();
        row.maDotNoiTru = model.maDotNoiTru();
        row.trangThaiTruoc = model.trangThaiTruoc();
        row.trangThaiSau = model.trangThaiSau();
        row.nguoiThucHien = model.nguoiThucHien();
        row.lyDo = model.lyDo();
        row.maTuongQuan = model.maTuongQuan();
        row.thoiGianThayDoi = model.thoiGianThayDoi();
        return row;
    }

    public OverrideJpaEntity override(UUID id, UUID admissionId, OverrideType type, UUID approvedBy,
                                      String role, String reason, Instant approvedAt) {
        OverrideJpaEntity row = new OverrideJpaEntity();
        row.maPheDuyet = id;
        row.maDotNoiTru = admissionId;
        row.loaiPheDuyet = type;
        row.nguoiDuyet = approvedBy;
        row.vaiTroNguoiDuyet = role;
        row.lyDo = reason;
        row.thoiGianDuyet = approvedAt;
        return row;
    }
}
