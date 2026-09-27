package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dot_noi_tru")
public class AdmissionJpaEntity {
    @Id @Column(name = "admission_id", nullable = false) public UUID maDotNoiTru;
    @Column(name = "admission_request_id", nullable = false, unique = true) public UUID maYeuCauNoiTru;
    @Column(name = "patient_id", nullable = false) public UUID maBenhNhan;
    @Column(name = "source_record_id", nullable = false) public UUID maHoSoNguon;
    @Column(name = "requested_by", nullable = false) public UUID nguoiYeuCau;
    @Column(name = "diagnosis_summary", nullable = false) public String tomTatChanDoan;
    @Column(name = "requested_at", nullable = false) public Instant thoiGianYeuCau;
    @Column(name = "attending_doctor_id") public UUID bacSiDieuTri;
    @Column(name = "department_id", nullable = false) public UUID maKhoa;
    @Enumerated(EnumType.STRING) @Column(name = "priority", nullable = false, length = 20) public AdmissionPriority doUuTien;
    @Column(name = "emergency", nullable = false) public boolean capCuu;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32) public AdmissionStatus trangThai;
    @Column(name = "deposit_clearance_id") public UUID maXacNhanTamUng;
    @Column(name = "deposit_requested_at") public Instant thoiGianYeuCauTamUng;
    @Column(name = "emergency_override_id") public UUID maPheDuyetCapCuu;
    @Column(name = "settlement_id") public UUID maQuyetToan;
    @Column(name = "close_override_id") public UUID maPheDuyetDong;
    @Column(name = "discharge_summary_id") public UUID maTomTatRaVien;
    @Column(name = "admitted_at") public Instant thoiGianNhapVien;
    @Column(name = "medically_discharged_at") public Instant thoiGianRaVienYTe;
    @Column(name = "closed_at") public Instant thoiGianDong;
    @Column(name = "cancelled_at") public Instant thoiGianHuy;
    @Column(name = "cancellation_reason") public String lyDoHuy;
    @Version @Column(name = "version", nullable = false) public long phienBan;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) public Instant taoLuc;
    @Column(name = "updated_at", nullable = false, insertable = false) public Instant capNhatLuc;

    public AdmissionJpaEntity() { }
}
